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
import scala.collection.mutable.SynchronizedSet
import scala.collection.mutable.HashSet

class MemoryStorage extends Storage {

	val storage = new HashSet[Entity]() with SynchronizedSet[Entity] {
		override def elemHashCode(key: Entity) = java.lang.System.identityHashCode(key)
	}

	override def reinitialize =
		for (entity <- storage)
			entity.addToLiveCache

	override def toStorage(
		statements: List[MassModificationStatement],
		assignments: List[(Var[Any], EntityValue[Any])],
		deletes: List[(Entity, List[(Var[Any], EntityValue[Any])])]): Unit = {

		for ((ref, value) <- assignments)
			if (ref.outerEntity != null)
				storage += ref.outerEntity
		for ((entity, map) <- deletes)
			storage -= entity
	}

	override def fromStorage(query: Query[_]): List[List[EntityValue[_]]] =
		List()

	override def isMemoryStorage = true

	override def migrate(action: MigrationAction): Unit = {}

}