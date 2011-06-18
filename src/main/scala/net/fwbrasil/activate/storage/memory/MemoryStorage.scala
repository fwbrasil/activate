package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.ManifestUtil._

trait MemoryStorage extends Storage {
	
	var storage: scala.collection.mutable.HashMap[String, Entity] = _
	
	initialize
	
	def initialize = 
		storage = 
			new scala.collection.mutable.HashMap[String, Entity] 
                 with java.io.Serializable 
                 with scala.collection.mutable.SynchronizedMap[String, Entity]
	
	override def reinitialize = 
		for((id, entity) <- storage)
			entity.addToLiveCache
	
	override def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]): Unit = {
		MemoryStorage.toStorage(storage, assignments, deletes)
	}
	
	override def isMemoryStorage = true
	
}

object MemoryStorage {
	def toStorage(
		    storage: scala.collection.mutable.HashMap[String, Entity], 
		    assignments: Map[Var[Any], EntityValue[Any]], 
		    deletes: Map[Var[Any], EntityValue[Any]]
	): Unit = {
		for((ref, value) <- assignments)
			if(!storage.contains(ref.outerEntity.id))
				storage += (ref.outerEntity.id -> ref.outerEntity)
		for((ref, value) <- deletes)
		  storage -= (ref.outerEntity.id)
	}
}