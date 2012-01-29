package net.fwbrasil.activate.storage.prevayler

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
		for (entity <- prevalentSystem.values)
			context.liveCache.cachedInstance(entity)
	}

	def snapshot =
		prevayler.takeSnapshot

	override def reinitialize =
		initialize

	override def store(insertMap: Map[Entity, Map[String, StorageValue]], updateMap: Map[Entity, Map[String, StorageValue]], deleteMap: Map[Entity, Map[String, StorageValue]]): Unit = {
		val inserts =
			for ((entity, propertyMap) <- insertMap)
				yield (entity.id -> propertyMap.filter(_._1 != "id"))
		val updates =
			for ((entity, propertyMap) <- updateMap)
				yield (entity.id -> propertyMap.filter(_._1 != "id"))
		val deletes =
			for ((entity, propertyMap) <- deleteMap)
				yield entity.id
		prevayler.execute(PrevaylerMemoryStorageTransaction(context, inserts ++ updates, deletes.toSet))
	}

	def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] =
		List()

	override def isMemoryStorage = true

}

case class PrevaylerMemoryStorageTransaction(context: ActivateContext, assignments: Map[String, Map[String, StorageValue]], deletes: Set[String]) extends PrevaylerTransaction {
	def executeOn(system: Object, date: java.util.Date) = {

		val storage = system.asInstanceOf[scala.collection.mutable.HashMap[String, Entity]]

		val liveCache = context.liveCache

		for ((entityId, changeSet) <- assignments) {
			val entity = materializeEntity(entityId)
			storage += (entity.id -> entity)
			for ((varName, value) <- changeSet) {
				entity.setInitialized
				val ref = entity.varNamed(varName).get.asInstanceOf[Var[Any]]
				val entityValue = Marshaller.unmarshalling(value) match {
					case value: EntityInstanceReferenceValue[_] =>
						if (value.value != None)
							ref.setRefContent(Option(materializeEntity(value.value.get)))
						else
							ref.setRefContent(None)
					case other: EntityValue[_] =>
						ref.setRefContent(other.value)
				}
			}
		}

		for (delete <- deletes) {
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
