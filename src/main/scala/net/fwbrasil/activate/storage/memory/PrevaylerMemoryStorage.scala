package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.{EntityValue, Entity, Var}
import net.fwbrasil.radon.transaction.Transaction
import org.prevayler.{Transaction => PrevaylerTransaction, PrevaylerFactory, Prevayler}

class PrevaylerMemoryStorage(implicit val context: ActivateContext) extends MemoryStorage {
	
	var prevayler: Prevayler = _
	
	lazy val name = "activate"

	initialize
	
	override def initialize = {
		super.initialize
		prevayler = PrevaylerFactory.createPrevayler(storage, name)
		storage = prevayler.prevalentSystem.asInstanceOf[scala.collection.mutable.HashMap[String, Entity]]
		for((id, entity) <- storage)
			entity.addToLiveCache
	}
		
	override def reinitialize = 
		initialize
		
	override def toStorage(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]): Unit =
		prevayler.execute(PrevaylerMemoryStorageTransaction(assignments, deletes))
	
}

case class PrevaylerMemoryStorageTransaction(assignments: Map[Var[Any], EntityValue[Any]], deletes: Map[Var[Any], EntityValue[Any]]) extends PrevaylerTransaction {
	def executeOn(system: Object, date: java.util.Date) = {
		
		for((ref: Var[Any], entityValue: EntityValue[Any]) <- assignments) {
			val outerEntity = ref.outerEntity
			if(!outerEntity.isInLiveCache) {
				outerEntity.varNamed(ref.name).get.asInstanceOf[Var[Any]].setRefContent(entityValue.value)
				outerEntity.setPersisted
			}
		}
		
		MemoryStorage.toStorage(system.asInstanceOf[scala.collection.mutable.HashMap[String, Entity]], assignments, deletes)
	}
}
