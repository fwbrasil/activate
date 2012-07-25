package net.fwbrasil.activate.storage.prevayler

import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.{ EntityValue, Entity, Var, EntityInstanceReferenceValue }
import net.fwbrasil.radon.transaction.Transaction
import org.prevayler.{ Transaction => PrevaylerTransaction, PrevaylerFactory, Prevayler }
import scala.collection.mutable.{ Map => MutableMap, Set => MutableSet }
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import java.util.HashMap
import java.util.HashSet
import scala.collection.JavaConversions._
import net.fwbrasil.activate.util.Reflection
import scala.annotation.implicitNotFound

class PrevaylerStorageSystem extends scala.collection.mutable.HashMap[String, Entity]

@implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
class PrevaylerStorage(implicit val context: ActivateContext) extends MarshalStorage[Prevayler] with Logging {

	protected[activate] var prevayler: Prevayler = _

	lazy val name = "activate"

	def directAccess =
		prevayler

	protected[activate] var prevalentSystem: PrevaylerStorageSystem = _

	initialize

	protected[activate] def initialize = {
		prevalentSystem = new PrevaylerStorageSystem()
		val factory = new PrevaylerFactory()
		factory.configureTransactionFiltering(false)
		factory.configurePrevalentSystem(prevalentSystem)
		factory.configurePrevalenceDirectory(name)
		PrevaylerStorage.isRecovering = true
		try {
			prevayler = factory.create
			prevalentSystem = prevayler.prevalentSystem.asInstanceOf[PrevaylerStorageSystem]
			prevalentSystem.values.foreach(Reflection.initializeBitmaps)
			prevalentSystem.values.foreach(_.invariants)

		} finally
			PrevaylerStorage.isRecovering = false
		for (entity <- prevalentSystem.values) {
			context.liveCache.toCache(entity)
		}
	}

	def snapshot =
		try {
			Entity.serializeUsingEvelope = false
			prevayler.takeSnapshot
		} finally {
			Entity.serializeUsingEvelope = true
		}

	override protected[activate] def reinitialize =
		initialize

	override protected[activate] def store(
		statements: List[MassModificationStatement],
		insertList: List[(Entity, Map[String, StorageValue])],
		updateList: List[(Entity, Map[String, StorageValue])],
		deleteList: List[(Entity, Map[String, StorageValue])]): Unit = {
		// Just ignore mass statements!
		val inserts =
			(for ((entity, propertyMap) <- insertList)
				yield (entity.id -> propertyMap.toList))
		val updates =
			(for ((entity, propertyMap) <- updateList)
				yield (entity.id -> propertyMap.toList))
		val deletes =
			for ((entity, propertyMap) <- deleteList)
				yield entity.id
		val assignments =
			new HashMap[String, HashMap[String, StorageValue]]((inserts ++ updates).toMap.mapValues(l => new HashMap[String, StorageValue](l.toMap)))
		prevayler.execute(new PrevaylerMemoryStorageTransaction(context, assignments, new HashSet(deletes)))
	}

	protected[activate] def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] =
		List()

	override protected[activate] def migrateStorage(action: ModifyStorageAction): Unit =
		logWarn("PrevaylerStorage ignores Migration actions (only customScritps are executed)") {}

	override def isMemoryStorage = true

}

object PrevaylerStorage {
	var isRecovering = false
}

class PrevaylerMemoryStorageTransaction(
	context: ActivateContext,
	assignments: HashMap[String, HashMap[String, StorageValue]],
	deletes: HashSet[String])
		extends PrevaylerTransaction {
	def executeOn(system: Object, date: java.util.Date) = {
		val storage = system.asInstanceOf[scala.collection.mutable.HashMap[String, Entity]]
		val liveCache = context.liveCache

		for ((entityId, changeSet) <- assignments)
			storage += (entityId -> liveCache.materializeEntity(entityId))

		for (entityId <- deletes)
			storage -= entityId

		if (PrevaylerStorage.isRecovering) {

			for ((entityId, changeSet) <- assignments) {
				val entity = liveCache.materializeEntity(entityId)
				entity.setInitialized
				for ((varName, value) <- changeSet; if (varName != "id")) {
					val ref = entity.varNamed(varName).get
					val entityValue = Marshaller.unmarshalling(value, ref.tval(None)) match {
						case value: EntityInstanceReferenceValue[_] =>
							if (value.value.isDefined)
								ref.setRefContent(Option(liveCache.materializeEntity(value.value.get)))
							else
								ref.setRefContent(None)
						case other: EntityValue[_] =>
							ref.setRefContent(other.value)
					}
				}
			}

			for (entityId <- deletes) {
				val entity = liveCache.materializeEntity(entityId)
				liveCache.delete(entityId)
				entity.setInitialized
				for (ref <- entity.vars)
					ref.destroyInternal
			}

		}
	}
}

object PrevaylerMemoryStorageFactory extends StorageFactory {
	override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] = {
		new PrevaylerStorage {
			override lazy val name = properties("name")
		}
	}
}
