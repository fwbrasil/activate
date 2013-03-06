package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.{ EntityValue, Var, Entity }
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.util.RichList._
import scala.collection.mutable.{ Map => MutableMap }
import scala.collection.JavaConversions._
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.Marshaller.marshalling
import net.fwbrasil.activate.storage.marshalling.Marshaller.unmarshalling
import java.util.IdentityHashMap
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.storage.TransactionHandle

trait MarshalStorage[T] extends Storage[T] {

    override protected[activate] def toStorage(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])]) =
        store(
            statements,
            marshalling(insertList),
            marshalling(updateList),
            marshalling(deleteList))

    private def marshalling(list: List[(Entity, Map[String, EntityValue[Any]])]) =
        list.map(tuple => (tuple._1, tuple._2.mapValues(Marshaller.marshalling(_)) + ("id" -> ReferenceStorageValue(Some(tuple._1.id)))))

    protected[activate] def store(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle]

    override protected[activate] def fromStorage(queryInstance: Query[_], entitiesReadFromCache: List[List[Entity]]): List[List[EntityValue[_]]] = {
        val entityValues =
            for (value <- queryInstance.select.values)
                yield value.entityValue
        val expectedTypes =
            (for (value <- entityValues)
                yield Marshaller.marshalling(value)).toList
        val result = query(queryInstance, expectedTypes, entitiesReadFromCache)
        (for (line <- result)
            yield (for (i <- 0 until line.size)
            yield Marshaller.unmarshalling(line(i), entityValues(i))).toList)
    }

    protected[activate] def query(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]]

    override protected[activate] def migrate(action: StorageAction): Unit =
        migrateStorage(Marshaller.marshalling(action))

    protected[activate] def migrateStorage(action: ModifyStorageAction): Unit

}

