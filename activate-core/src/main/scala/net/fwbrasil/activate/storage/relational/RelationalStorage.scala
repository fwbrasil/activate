package net.fwbrasil.activate.storage.relational

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.util.GraphUtil.CyclicReferenceException
import net.fwbrasil.activate.util.GraphUtil.DependencyTree
import net.fwbrasil.activate.util.Reflection.NiceObject

trait RelationalStorage[T] extends MarshalStorage[T] {

    protected def noEscape(string: String) = string
    protected def underscoreSeparated(string: String) = {
        val chars =
            List(string.head.toLower) ++ string.tail.collect {
                case e if (e.isUpper) =>
                    "_" + e.toLower
                case e =>
                    e
            }
        chars.mkString("")
    }

    def store(
        readList: List[(BaseEntity, Long)],
        statementList: List[MassModificationStatement],
        insertList: List[(BaseEntity, Map[String, StorageValue])],
        updateList: List[(BaseEntity, Map[String, StorageValue])],
        deleteList: List[(BaseEntity, Map[String, StorageValue])]): Option[TransactionHandle] =
        executeStatements(verionsFor(readList), statementsFor(statementList, insertList, updateList, deleteList))

    override def storeAsync(
        readList: List[(BaseEntity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(BaseEntity, Map[String, StorageValue])],
        updateList: List[(BaseEntity, Map[String, StorageValue])],
        deleteList: List[(BaseEntity, Map[String, StorageValue])])(implicit ecxt: ExecutionContext): Future[Unit] =
        executeStatementsAsync(verionsFor(readList), statementsFor(statements, insertList, updateList, deleteList))

    private def verionsFor(readList: List[(BaseEntity, Long)]) =
        readList.groupBy(_._1.niceClass).mapValues(_.map(tuple => (Marshaller.idMarshalling(Some(tuple._1.id), tuple._1.niceClass), tuple._2)))

    private def sortToAvoidDeadlocks(list: List[(BaseEntity, Map[String, StorageValue])]) =
        list.sortBy(_._1.id.toString)

    protected[activate] def resolveDependencies(statements: Set[DmlStorageStatement]): List[DmlStorageStatement] =
        if (statements.size <= 1)
            statements.toList
        else
            try {
                val entityInsertMap = statements.groupBy(_.entityId).mapValues(_.head)
                val tree = new DependencyTree[DmlStorageStatement](statements)
                for (insertA <- statements) {
                    for ((propertyName, propertyValue) <- insertA.propertyMap) {
                        propertyValue match {
                            case propertyValue: ReferenceStorageValue if (propertyName != "id" && propertyValue.value.value.isDefined) =>
                                val entityIdB = propertyValue.value.value.get.asInstanceOf[BaseEntity#ID]
                                if (entityInsertMap.contains(entityIdB))
                                    tree.addDependency(entityInsertMap(entityIdB), insertA)
                            case ListStorageValue(Some(values: List[StorageValue]), _: ReferenceStorageValue) =>
                                val ids =
                                    (values.collect {
                                        case value: ReferenceStorageValue =>
                                            value.value.value
                                        case value: StringStorageValue =>
                                            value.value
                                    }).flatten.asInstanceOf[List[BaseEntity#ID]]
                                for (entityIdB <- ids)
                                    if (entityInsertMap.contains(entityIdB))
                                        tree.addDependency(entityInsertMap(entityIdB), insertA)
                            case other =>
                        }
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

    protected[activate] def executeStatements(reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]], statements: List[StorageStatement]): Option[TransactionHandle]

    protected[activate] def executeStatementsAsync(reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]], statements: List[StorageStatement])(implicit context: ExecutionContext): Future[Unit] =
        blockingFuture(executeStatements(reads, statements).map(_.commit))

    private def statementsFor(
        statementList: List[MassModificationStatement],
        insertList: List[(BaseEntity, Map[String, StorageValue])],
        updateList: List[(BaseEntity, Map[String, StorageValue])],
        deleteList: List[(BaseEntity, Map[String, StorageValue])]) = {

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