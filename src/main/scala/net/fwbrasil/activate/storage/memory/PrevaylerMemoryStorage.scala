package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.query.Query
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.{ EntityValue, Entity, Var, EntityInstanceReferenceValue }
import net.fwbrasil.radon.transaction.Transaction
import org.prevayler.{ Transaction => PrevaylerTransaction, PrevaylerFactory, Prevayler }
import scala.collection.mutable.{ Map => MutableMap, Set => MutableSet }

class PrevaylerMemoryStorage(implicit val context: ActivateContext) extends MarshalStorage {

	var prevayler: Prevayler = _

	lazy val name = "activate"

	var prevalentSystem: scala.collection.mutable.HashMap[String, Entity] = _

	initialize

	def initialize = {
		prevalentSystem = scala.collection.mutable.HashMap[String, Entity]()
		val factory = new PrevaylerFactory()
		factory.configureTransactionFiltering(false)
		factory.configurePrevalentSystem(prevalentSystem)
		factory.configurePrevalenceDirectory(name)
		prevayler = factory.create
		prevalentSystem = prevayler.prevalentSystem.asInstanceOf[scala.collection.mutable.HashMap[String, Entity]]
	}

	override def reinitialize =
		initialize

	def store(pAssignments: Map[Var[_], StorageValue], pDeletes: Map[Var[Any], StorageValue]): Unit = {
		val assignments = MutableMap[String, Set[(String, StorageValue)]]()
		val deletes =
			(for ((ref, value) <- pDeletes)
				yield ref.outerEntity.id).toSet
		for ((ref, value) <- pAssignments; if(!deletes.contains(ref.outerEntity.id)))
			assignments.put(ref.outerEntity.id, assignments.getOrElse(ref.outerEntity.id, Set()) + Tuple2(ref.name, value))
		prevayler.execute(PrevaylerMemoryStorageTransaction(context, assignments.toMap, deletes))
	}

	def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] =
		List()
		
	override def isMemoryStorage = true

}

case class PrevaylerMemoryStorageTransaction(context: ActivateContext, assignments: Map[String, Set[(String, StorageValue)]], deletes: Set[String]) extends PrevaylerTransaction {
	def executeOn(system: Object, date: java.util.Date) = {

		val storage = system.asInstanceOf[scala.collection.mutable.HashMap[String, Entity]]

		val liveCache = context.liveCache
		
		for ((entityId, changeSet) <- assignments) {
			val entity = materializeEntity(entityId)
			storage += (entity.id -> entity)
//			if (!entity.isInitialized) {
				for ((varName, value) <- changeSet) {
					entity.setInitialized
					val ref = entity.varNamed(varName).get.asInstanceOf[Var[Any]]
					val entityValue = Marshaller.unmarshalling(value) match {
						case value: EntityInstanceReferenceValue[_] =>
							if(value.value != None)
								ref.setRefContent(Option(materializeEntity(value.value.get)))
							else
								ref.setRefContent(None)
						case other: EntityValue[_] =>
							ref.setRefContent(other.value)
					}
				}
//			}
		}
		
		for(delete <- deletes) {
			storage -= delete
			liveCache.delete(delete)
		}

		def materializeEntity(entityId: String) = {
			val entity = liveCache.materializeEntity(entityId)
			storage += (entity.id -> entity)
			entity
		}

	}
}
