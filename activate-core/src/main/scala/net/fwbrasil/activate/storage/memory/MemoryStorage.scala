package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import scala.collection.mutable.{ HashMap => MutableHashMap }
import net.fwbrasil.activate.storage.marshalling.StorageMigrationAction
import net.fwbrasil.activate.migration.MigrationAction
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.mass.MassModificationStatement

class MemoryStorage extends Storage {

	val storage: MutableHashMap[String, Entity] =
		new MutableHashMap[String, Entity] with java.io.Serializable with scala.collection.mutable.SynchronizedMap[String, Entity]

	override def reinitialize =
		for ((id, entity) <- storage)
			entity.addToLiveCache

	override def toStorage(statements: List[MassModificationStatement], assignments: List[(Var[Any], EntityValue[Any])], deletes: List[(Entity, Map[Var[Any], EntityValue[Any]])]): Unit = {
		MemoryStorage.toStorage(storage, assignments, deletes)
	}

	override def fromStorage(query: Query[_]): List[List[EntityValue[_]]] =
		List()

	override def isMemoryStorage = true

	override def migrate(action: MigrationAction): Unit = {}

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