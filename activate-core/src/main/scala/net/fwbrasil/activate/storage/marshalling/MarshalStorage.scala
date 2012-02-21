package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.{ EntityValue, Var, Entity }
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.util.RichList._
import scala.collection.mutable.{ Map => MutableMap }
import scala.collection.JavaConversions._
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.query.Query
import net.fwbrasil.activate.storage.marshalling.Marshaller.marshalling
import net.fwbrasil.activate.storage.marshalling.Marshaller.unmarshalling
import java.util.IdentityHashMap

trait MarshalStorage extends Storage {

	override def toStorage(assignments: List[(Var[Any], EntityValue[Any])], deletes: List[(Entity, Map[Var[Any], EntityValue[Any]])]): Unit = {

		import Marshaller._

		val insertMap = MutableMap[String, MutableMap[String, StorageValue]]()
		val updateMap = MutableMap[String, MutableMap[String, StorageValue]]()
		val deleteMap = MutableMap[String, MutableMap[String, StorageValue]]()
		val entityMap = MutableMap[String, Entity]()

		def propertyMap(map: MutableMap[String, MutableMap[String, StorageValue]], entity: Entity) = {
			entityMap += (entity.id -> entity)
			map.getOrElseUpdate(entity.id, newPropertyMap(entity))
		}

		for ((entity, properties) <- deletes) {
			val map = propertyMap(deleteMap, entity)
			for ((ref, value) <- properties) map.put(ref.name, marshalling(value))
		}

		for ((ref, value) <- assignments) {
			val entity = ref.outerEntity
			val propertyName = ref.name
			if (!deletes.contains(entity))
				if (!entity.isPersisted)
					propertyMap(insertMap, entity) += (propertyName -> marshalling(value))
				else
					propertyMap(updateMap, entity) += (propertyName -> marshalling(value))
		}

		val insertList =
			for ((entityId, properties) <- insertMap.toList)
				yield (entityMap(entityId), properties.toMap)

		val updateList =
			for ((entityId, properties) <- updateMap.toList)
				yield (entityMap(entityId), properties.toMap)

		val deleteList =
			for ((entityId, properties) <- deleteMap.toList)
				yield (entityMap(entityId), properties.toMap)

		store(insertList, updateList, deleteList)
	}

	private[this] def newPropertyMap(entity: Entity) =
		MutableMap("id" -> (ReferenceStorageValue(Option(entity.id))).asInstanceOf[StorageValue])

	override def fromStorage(queryInstance: Query[_]): List[List[EntityValue[_]]] = {
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

	def store(insertMap: List[(Entity, Map[String, StorageValue])], updateMap: List[(Entity, Map[String, StorageValue])], deleteSet: List[(Entity, Map[String, StorageValue])]): Unit

	def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]]

}