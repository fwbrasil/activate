package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import scala.collection.mutable.{ HashMap => MutableHashMap }

trait MemoryStorage extends Storage {

	var storage: MutableHashMap[String, Entity] = _

	initialize

	def initialize =
		storage = new MutableHashMap[String, Entity] with java.io.Serializable with scala.collection.mutable.SynchronizedMap[String, Entity]

	override def reinitialize =
		for ((id, entity) <- storage)
			entity.addToLiveCache

	override def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Set[Entity]): Unit = {
		MemoryStorage.toStorage(storage, assignments, deletes)
	}

	override def isMemoryStorage = true

}

object MemoryStorage {
	def toStorage(
		storage: MutableHashMap[String, Entity],
		assignments: Map[Var[Any], EntityValue[Any]],
		deletes: Set[Entity]): Unit = {
		for ((ref, value) <- assignments)
			if (ref.outerEntity != null && !storage.contains(ref.outerEntity.id))
				storage += (ref.outerEntity.id -> ref.outerEntity)
		for (entity <- deletes)
			storage -= (entity.id)
	}
}