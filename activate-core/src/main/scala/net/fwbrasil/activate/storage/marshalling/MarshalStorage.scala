package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.{ EntityValue, Var, Entity }
import net.fwbrasil.activate.query._
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.entity.EntityInstanceEntityValue

trait MarshalStorage extends Storage {

	override def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]): Unit = {

		import Marshaller._

		val deleteMap = MutableMap[Entity, MutableMap[String, StorageValue]]()
		val insertMap = MutableMap[Entity, MutableMap[String, StorageValue]]()
		val updateMap = MutableMap[Entity, MutableMap[String, StorageValue]]()
		
		for ((ref, value) <- deletes) {
			val entity = ref.outerEntity
			val propertyName = ref.name

			deleteMap.getOrElseUpdate(entity, {
				MutableMap[String, StorageValue]("id" -> (StringStorageValue(Option(entity.id))(EntityInstanceEntityValue(Option(entity)))).asInstanceOf[StorageValue])
			}) += (propertyName -> marshalling(value))

		}

		for ((ref, value) <- assignments if (!ref.outerEntity.isPersisted && !deleteMap.contains(ref.outerEntity))) {
			val entity = ref.outerEntity
			insertMap += (entity -> MutableMap[String, StorageValue]())
			insertMap(entity) += ("id" -> (StringStorageValue(Option(entity.id))(EntityInstanceEntityValue(Option(entity))).asInstanceOf[StorageValue]))
		}

		for ((ref, value) <- assignments if (!deleteMap.contains(ref.outerEntity))) {
			val entity = ref.outerEntity
			val propertyName = ref.name

			if (insertMap.contains(entity)) {
				insertMap(entity) += (propertyName -> marshalling(value))
			} else {
				if (!updateMap.contains(entity)) {
					updateMap += (entity -> MutableMap[String, StorageValue]())
					updateMap(entity) += ("id" -> (StringStorageValue(Option(entity.id))(EntityInstanceEntityValue(Option(entity)))).asInstanceOf[StorageValue])
				}
				updateMap(entity) += (propertyName -> marshalling(value))
			}
		}

		
		store(insertMap.mapValues(_.toMap).toMap, updateMap.mapValues(_.toMap).toMap, deleteMap.mapValues(_.toMap).toMap)
	}

	override def fromStorage(queryInstance: Query[_]): List[List[EntityValue[_]]] = {
		val expectedTypes =
			(for (value <- queryInstance.select.values)
				yield Marshaller.marshalling(value)).toList
		val result = query(queryInstance, expectedTypes)
		(for (line <- result)
			yield for (column <- line)
			yield Marshaller.unmarshalling(column))
	}

	def store(insertMap: Map[Entity, Map[String, StorageValue]], updateMap: Map[Entity, Map[String, StorageValue]], deleteMap: Map[Entity, Map[String, StorageValue]]): Unit

	def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]]

}