package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.{EntityValue, Var, Entity}
import net.fwbrasil.activate.query._
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.util.CollectionUtil.toTuple

trait MarshalStorage extends Storage {
	
	override def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]): Unit = {
		store(assignments.mapValues {
			(entityValue: EntityValue[Any]) =>
				Marshaller.marshalling(entityValue)
		}.asInstanceOf[Map[Var[_], StorageValue]], deletes.mapValues {
			(entityValue: EntityValue[Any]) =>
				Marshaller.marshalling(entityValue)
		})
	}
	
	override def fromStorage(queryInstance: Query[_]): List[List[EntityValue[_]]] = {
		val expectedTypes = 
			(for(value <- queryInstance.select.values)
				yield Marshaller.marshalling(value)).toList
		val result = query(queryInstance, expectedTypes)
		(for(line <- result)
		   yield for(column <- line)
				yield Marshaller.unmarshalling(column))
	}
	
	def store(assignments: Map[Var[_], StorageValue], deletes: Map[Var[Any], StorageValue]): Unit
	
	def query(query: Query[_], expectedTypes: List[StorageValue]) : List[List[StorageValue]]

}