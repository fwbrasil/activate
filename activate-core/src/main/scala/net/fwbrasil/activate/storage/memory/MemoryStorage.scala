package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import scala.collection.mutable.{ HashMap => MutableHashMap }

class MemoryStorage extends Storage {

	val storage: MutableHashMap[String, Entity] =
		new MutableHashMap[String, Entity] with java.io.Serializable with scala.collection.mutable.SynchronizedMap[String, Entity]

	override def reinitialize =
		for ((id, entity) <- storage)
			entity.addToLiveCache

	override def toStorage(assignments: List[(Var[Any], EntityValue[Any])], deletes: List[(Entity, Map[Var[Any], EntityValue[Any]])]): Unit = {
		MemoryStorage.toStorage(storage, assignments, deletes)
	}

	override def isMemoryStorage = true

}

object MemoryStorage {
	def toStorage(
		storage: MutableHashMap[String, Entity],
		assignments: List[(Var[Any], EntityValue[Any])],
		deletes: List[(Entity, Map[Var[Any], EntityValue[Any]])]): Unit = {
		for ((ref, value) <- assignments)
			if (ref.outerEntity != null && !storage.contains(ref.outerEntity.id))
				storage += (ref.outerEntity.id -> ref.outerEntity)
		for ((entity, map) <- deletes)
			storage -= (entity.id)
	}
}