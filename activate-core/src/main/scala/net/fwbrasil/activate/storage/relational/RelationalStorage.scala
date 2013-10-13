package net.fwbrasil.activate.storage.relational

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.util.GraphUtil.CyclicReferenceException
import net.fwbrasil.activate.util.GraphUtil.DependencyTree
import net.fwbrasil.activate.util.Reflection.NiceObject

trait RelationalStorage[T] extends MarshalStorage[T] {

    def store(
        readList: List[(Entity, Long)],
        statementList: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] =
        executeStatements(verionsFor(readList), statementsFor(statementList, insertList, updateList, deleteList))

    override def storeAsync(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])])(implicit ecxt: ExecutionContext): Future[Unit] =
        executeStatementsAsync(verionsFor(readList), statementsFor(statements, insertList, updateList, deleteList))
        
    private def verionsFor(readList: List[(Entity, Long)]) = {
        readList.groupBy(_._1.niceClass).mapValues(_.map(tuple => (tuple._1.id, tuple._2)))
    }

    private def sortToAvoidDeadlocks(list: List[(Entity, Map[String, StorageValue])]) =
        list.sortBy(_._1.id)

    protected[activate] def resolveDependencies(statements: Set[DmlStorageStatement]): List[DmlStorageStatement] =
        if (statements.size <= 1)
            statements.toList
        else
            try {
                val entityInsertMap = statements.groupBy(_.entityId).mapValues(_.head)
                val tree = new DependencyTree[DmlStorageStatement](statements)
                for (insertA <- statements) {
                    for ((propertyName, propertyValue) <- insertA.propertyMap)
                        if (propertyName != "id" && propertyValue.value.isDefined && propertyValue.isInstanceOf[ReferenceStorageValue]) {
                            val entityIdB = propertyValue.value.get.asInstanceOf[String]
                            if (entityInsertMap.contains(entityIdB))
                                tree.addDependency(entityInsertMap(entityIdB), insertA)
                        }
                }
                tree.resolve
            } catch {
                case e: CyclicReferenceException =>
                    // Let storage cry if necessary!
                    statements.toList
            }

    override protected[activate] def migrateStorage(action: ModifyStorageAction): Unit =
        executeStatements(Map(), List(DdlStorageStatement(action))).map(_.commit)

    protected[activate] def executeStatements(reads: Map[Class[Entity], List[(String, Long)]], statements: List[StorageStatement]): Option[TransactionHandle]

    protected[activate] def executeStatementsAsync(reads: Map[Class[Entity], List[(String, Long)]], statements: List[StorageStatement])(implicit context: ExecutionContext): Future[Unit] =
        blockingFuture(executeStatements(reads, statements).map(_.commit))

    private def statementsFor(
        statementList: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]) = {

        val statements =
            statementList.map(ModifyStorageStatement(_))

        val inserts =
            for ((entity, propertyMap) <- insertList)
                yield InsertStorageStatement(entity.niceClass, entity.id, propertyMap)

        val insertsResolved = resolveDependencies(inserts.toSet)

        val updates =
            for ((entity, propertyMap) <- sortToAvoidDeadlocks(updateList))
                yield UpdateStorageStatement(entity.niceClass, entity.id, propertyMap)

        val deletes =
            for ((entity, propertyMap) <- deleteList)
                yield DeleteStorageStatement(entity.niceClass, entity.id, propertyMap)

        val deletesResolved = resolveDependencies(deletes.toSet).reverse

        statements ::: insertsResolved ::: updates.toList ::: deletesResolved
    }

}