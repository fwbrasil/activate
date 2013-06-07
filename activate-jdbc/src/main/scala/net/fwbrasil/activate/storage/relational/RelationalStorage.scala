package net.fwbrasil.activate.storage.relational

import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.util.GraphUtil._
import net.fwbrasil.activate.util.Reflection._
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.storage.TransactionHandle
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait RelationalStorage[T] extends MarshalStorage[T] {

    def store(
        statementList: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] =
        executeStatements(statementsFor(statementList, insertList, updateList, deleteList))

    override def storeAsync(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])])(implicit ecxt: ExecutionContext): Future[Unit] =
        executeStatementsAsync(statementsFor(statements, insertList, updateList, deleteList))

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
                        if (propertyName != "id" && propertyValue.value != None && propertyValue.isInstanceOf[ReferenceStorageValue]) {
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
        executeStatements(List(DdlStorageStatement(action))).map(_.commit)

    protected[activate] def executeStatements(sqls: List[StorageStatement]): Option[TransactionHandle]

    protected[activate] def executeStatementsAsync(sqls: List[StorageStatement])(implicit context: ExecutionContext): Future[Unit] =
        blockingFuture(executeStatements(sqls).map(_.commit))

    private def statementsFor(statementList: List[net.fwbrasil.activate.statement.mass.MassModificationStatement], insertList: List[(net.fwbrasil.activate.entity.Entity, Map[String, net.fwbrasil.activate.storage.marshalling.StorageValue])], updateList: List[(net.fwbrasil.activate.entity.Entity, Map[String, net.fwbrasil.activate.storage.marshalling.StorageValue])], deleteList: List[(net.fwbrasil.activate.entity.Entity, Map[String, net.fwbrasil.activate.storage.marshalling.StorageValue])]): List[net.fwbrasil.activate.storage.relational.StorageStatement] = {

        val statements =
            statementList.map(ModifyStorageStatement(_))

        val inserts =
            for ((entity, propertyMap) <- insertList)
                yield InsertDmlStorageStatement(entity.niceClass, entity.id, propertyMap)

        val insertsResolved = resolveDependencies(inserts.toSet)

        val updates =
            for ((entity, propertyMap) <- sortToAvoidDeadlocks(updateList))
                yield UpdateDmlStorageStatement(entity.niceClass, entity.id, propertyMap)

        val deletes =
            for ((entity, propertyMap) <- deleteList)
                yield DeleteDmlStorageStatement(entity.niceClass, entity.id, propertyMap)

        val deletesResolved = resolveDependencies(deletes.toSet).reverse

        val sqls = statements ::: insertsResolved ::: updates.toList ::: deletesResolved
        sqls
    }

}