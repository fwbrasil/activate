package net.fwbrasil.activate.storage.map

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.util.ManifestUtil._

trait MapStorage extends Storage {

	override def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]): Unit = {
		val propertiesAssignments = 
			for((ref, value) <- assignments)
				yield PropertyAssignment(
						MapStoragePath(ref), 
						toStorageValue(value))
		toStorage(propertiesAssignments.toSet.asInstanceOf[Set[PropertyAssignment[Entity, Any]]])
	}
	
	def toStorageValue(value: EntityValue[Any]) =
		(value match {
			case entityValue: EntityInstanceEntityValue[_] => 
				MapStoragePath(entityValue.value.get)(manifestClass(entityValue.value.getClass))
			case other => 
				other.value
		}).asInstanceOf[Option[Any]]
	
	def toStorage(assignments: Set[PropertyAssignment[Entity, Any]]): Unit
}

case class PropertyAssignment
	[E <: Entity: Manifest, P: Manifest]
	(path: EntityPropetyPath[E,P], value: Option[P])
