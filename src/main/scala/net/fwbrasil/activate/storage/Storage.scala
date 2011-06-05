package net.fwbrasil.activate.storage

import net.fwbrasil.radon.transaction.TransactionContext
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.query.Query
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import scala.collection.mutable.{ Set => MutableSet, Map => MutableMap }

trait Storage {
	
	def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]): Unit
	def fromStorage(query: Query[_]): List[List[EntityValue[_]]] =
		List()
	
	def isMemoryStorage = false
	def reinitialize = {
		
	}

}
