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

trait MarshalStorage[T] extends Storage[T] {

	override protected[activate] def toStorage(
		statements: List[MassModificationStatement],
		assignments: List[(Var[Any], EntityValue[Any])],
		deletes: List[(Entity, List[(Var[Any], EntityValue[Any])])]): Unit = {

		import Marshaller._

			def propertyMap(map: IdentityHashMap[Entity, MutableMap[String, StorageValue]], entity: Entity) =
				if (!map.containsKey(entity)) {
					val propertyMap = newPropertyMap(entity)
					map.put(entity, propertyMap)
					propertyMap
				} else
					map.get(entity)

		// This code is ugly, but is faster! :(			
		val insertMap = new IdentityHashMap[Entity, MutableMap[String, StorageValue]]()
		val updateMap = new IdentityHashMap[Entity, MutableMap[String, StorageValue]]()
		for ((ref, value) <- assignments) {
			val entity = ref.outerEntity
			val propertyName = ref.name
			if (!entity.isPersisted)
				propertyMap(insertMap, entity).put(propertyName, marshalling(value))
			else
				propertyMap(updateMap, entity).put(propertyName, marshalling(value))
		}

		val deleteList =
			deletes.map(tuple => (tuple._1, tuple._2.map(t => (t._1.name, marshalling(t._2))).toMap))

		val insertList =
			insertMap.toList.map(tuple => (tuple._1, tuple._2.toMap))

		val updateList =
			updateMap.toList.map(tuple => (tuple._1, tuple._2.toMap))

		store(statements, insertList, updateList, deleteList)
	}

	private[this] def newPropertyMap(entity: Entity) =
		MutableMap("id" -> (ReferenceStorageValue(Option(entity.id))).asInstanceOf[StorageValue])

	override protected[activate] def fromStorage(queryInstance: Query[_]): List[List[EntityValue[_]]] = {
		val entityValues =
			for (value <- queryInstance.select.values)
				yield value.entityValue
		val expectedTypes =
			(for (value <- entityValues)
				yield marshalling(value)).toList
		val result = query(queryInstance, expectedTypes)
		(for (line <- result)
			yield (for (i <- 0 until line.size)
			yield unmarshalling(line(i), entityValues(i))).toList)
	}

	protected[activate] def store(
		statements: List[MassModificationStatement],
		insertList: List[(Entity, Map[String, StorageValue])],
		updateList: List[(Entity, Map[String, StorageValue])],
		deleteList: List[(Entity, Map[String, StorageValue])]): Unit

	protected[activate] def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]]

	override protected[activate] def migrate(action: StorageAction): Unit =
		migrateStorage(Marshaller.marshalling(action))

	protected[activate] def migrateStorage(action: ModifyStorageAction): Unit

}

