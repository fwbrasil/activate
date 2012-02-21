package net.fwbrasil.activate.storage

import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.query.Query
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity

trait Storage {

	def toStorage(assignments: List[(Var[Any], EntityValue[Any])], deletes: List[(Entity, Map[Var[Any], EntityValue[Any]])]): Unit
	def fromStorage(query: Query[_]): List[List[EntityValue[_]]] =
		List()

	def isMemoryStorage = false
	def supportComplexQueries = true
	def reinitialize = {

	}

}
