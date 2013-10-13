package net.fwbrasil.activate.storage.marshalling

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.radon.transaction.TransactionalExecutionContext

trait MarshalStorage[T] extends Storage[T] {

    override protected[activate] def toStorage(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])]) =
        store(
            readList,
            statements,
            marshalling(insertList),
            marshalling(updateList),
            marshalling(deleteList))

    override protected[activate] def toStorageAsync(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])])(implicit ecxt: ExecutionContext): Future[Unit] =
        storeAsync(
            readList,
            statements,
            marshalling(insertList),
            marshalling(updateList),
            marshalling(deleteList))

    private def marshalling(list: List[(Entity, Map[String, EntityValue[Any]])]) =
        list.map(tuple => (tuple._1, tuple._2.mapValues(Marshaller.marshalling(_)) + ("id" -> ReferenceStorageValue(Some(tuple._1.id)))))

    protected[activate] def store(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle]

    protected[activate] def storeAsync(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])])(implicit ecxt: ExecutionContext): Future[Unit] =
        blockingFuture(store(readList, statements, insertList, updateList, deleteList).map(_.commit))

    override def fromStorage(
        queryInstance: Query[_], entitiesReadFromCache: List[List[Entity]]): List[List[EntityValue[_]]] = {
        val (entityValues, expectedTypes) = prepareQuery(queryInstance)
        val lines = query(queryInstance, expectedTypes, entitiesReadFromCache)
        mapLines(lines, entityValues)
    }

    override protected[activate] def fromStorageAsync(
        queryInstance: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit ecxt: TransactionalExecutionContext): Future[List[List[EntityValue[_]]]] = {
        val (entityValues, expectedTypes) = prepareQuery(queryInstance)
        queryAsync(queryInstance, expectedTypes, entitiesReadFromCache)
            .map(mapLines(_, entityValues))
    }

    protected[activate] def query(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]]

    protected[activate] def queryAsync(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]])(implicit context: TransactionalExecutionContext): Future[List[List[StorageValue]]] =
        blockingFuture(this.query(query, expectedTypes, entitiesReadFromCache))

    override protected[activate] def migrate(action: StorageAction): Unit =
        migrateStorage(Marshaller.marshalling(action))

    protected[activate] def migrateStorage(action: ModifyStorageAction): Unit

    private def prepareQuery(queryInstance: net.fwbrasil.activate.statement.query.Query[_]): (Seq[net.fwbrasil.activate.entity.EntityValue[_]], List[net.fwbrasil.activate.storage.marshalling.StorageValue]) = {
        val entityValues =
            for (value <- queryInstance.select.values)
                yield value.entityValue
        val expectedTypes =
            (for (value <- entityValues)
                yield Marshaller.marshalling(value)).toList
        (entityValues, expectedTypes)
    }

    private def mapLines(lines: List[List[StorageValue]], entityValues: Seq[EntityValue[_]]) =
        for (line <- lines)
            yield (for (i <- 0 until line.size)
            yield Marshaller.unmarshalling(line(i), entityValues(i))).toList

}

