package net.fwbrasil.activate.storage

import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.query.Query
import net.fwbrasil.activate.entity.EntityValue

trait Storage {

	def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]): Unit
	def fromStorage(query: Query[_]): List[List[EntityValue[_]]] =
		List()

	def isMemoryStorage = false
	def supportComplexQueries = true
	def reinitialize = {

	}

}
