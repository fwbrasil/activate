package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.{ EntityValue, Var, Entity }
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.util.RichList._
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.query.Query

trait MarshalStorage extends Storage {

	override def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Set[Entity]): Unit = {

		import Marshaller._

		val insertMap = MutableMap[Entity, MutableMap[String, StorageValue]]()
		val updateMap = MutableMap[Entity, MutableMap[String, StorageValue]]()

		def propertyMap(map: MutableMap[Entity, MutableMap[String, StorageValue]], entity: Entity) =
			map.getOrElseUpdate(entity, newPropertyMap(entity))

		for ((ref, value) <- assignments) {
			val entity = ref.outerEntity
			val propertyName = ref.name
			if (!deletes.contains(entity)) {
				if (!entity.isPersisted)
					propertyMap(insertMap, entity) += (propertyName -> marshalling(value))
				else
					propertyMap(updateMap, entity) += (propertyName -> marshalling(value))
			}
		}

		store(insertMap.mapValues(_.toMap).toMap, updateMap.mapValues(_.toMap).toMap, deletes)
	}

	private[this] def newPropertyMap(entity: Entity) =
		MutableMap("id" -> (StringStorageValue(Option(entity.id))(EntityInstanceEntityValue(Option(entity)))).asInstanceOf[StorageValue])

	override def fromStorage(queryInstance: Query[_]): List[List[EntityValue[_]]] = {
		val expectedTypes =
			(for (value <- queryInstance.select.values)
				yield Marshaller.marshalling(value)).toList
		val result = query(queryInstance, expectedTypes)
		(for (line <- result)
			yield for (column <- line)
			yield Marshaller.unmarshalling(column))
	}

	def store(insertMap: Map[Entity, Map[String, StorageValue]], updateMap: Map[Entity, Map[String, StorageValue]], deleteSet: Set[Entity]): Unit

	def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]]

}