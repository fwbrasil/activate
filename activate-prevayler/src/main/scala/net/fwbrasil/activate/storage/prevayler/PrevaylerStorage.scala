package net.fwbrasil.activate.storage.prevayler

import java.util.HashMap
import java.util.HashSet
import scala.annotation.implicitNotFound
import scala.collection.JavaConversions.asScalaSet
import scala.collection.JavaConversions.mapAsJavaMap
import scala.collection.JavaConversions.mapAsScalaMap
import scala.collection.JavaConversions.seqAsJavaList
import org.prevayler.implementation.publishing.AbstractPublisher
import org.prevayler.implementation.publishing.TransactionSubscriber
import org.prevayler.implementation.PrevalentSystemGuard
import org.prevayler.Prevayler
import org.prevayler.PrevaylerFactory
import org.prevayler.{ Transaction => PrevaylerTransaction }
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.ActivateContext
import org.prevayler.implementation.TransactionTimestamp
import org.prevayler.implementation.Capsule
import java.util.Date
import org.prevayler.foundation.serialization.Serializer
import org.prevayler.implementation.TransactionCapsule
import org.prevayler.implementation.DummyTransactionCapsule
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.entity.Var

class PrevaylerStorageSystem extends scala.collection.mutable.HashMap[String, Entity] with scala.collection.mutable.SynchronizedMap[String, Entity]

@implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
class PrevaylerStorage(
    val factory: PrevaylerFactory[PrevaylerStorageSystem])(implicit val context: ActivateContext)
        extends MarshalStorage[Prevayler[PrevaylerStorageSystem]] {

    protected[activate] var prevayler: Prevayler[PrevaylerStorageSystem] = _

    def this(prevalenceDirectory: String)(implicit context: ActivateContext) = this({
        val res = new PrevaylerFactory[PrevaylerStorageSystem]()
        res.configurePrevalenceDirectory(prevalenceDirectory)
        res
    })
    def this()(implicit context: ActivateContext) = this("activate")

    def directAccess =
        prevayler

    def isMemoryStorage = true
    def isSchemaless = true
    def isTransactional = true
    def supportsQueryJoin = true

    protected[activate] var prevalentSystem: PrevaylerStorageSystem = _

    initialize

    protected[activate] def initialize = {
        prevalentSystem = new PrevaylerStorageSystem()
        factory.configurePrevalentSystem(prevalentSystem)
        prevayler = factory.create()
        prevalentSystem = prevayler.prevalentSystem.asInstanceOf[PrevaylerStorageSystem]
        prevalentSystem.values.foreach(Reflection.initializeBitmaps)
        prevalentSystem.values.foreach(_.invariants)
        hackPrevaylerToActAsARedoLogOnly
        for (entity <- prevalentSystem.values) {
            context.transactional(context.transient) {
                initializeLazyFlags(entity)
            }
            context.liveCache.toCache(entity)
        }
    }

    private def initializeLazyFlags(entity: net.fwbrasil.activate.entity.Entity): Unit = {
        val metadata = EntityHelper.getEntityMetadata(entity.getClass)
        val lazyFlags = metadata.propertiesMetadata.filter(p => p.isLazyFlag && p.isTransient)
        for(propertyMetadata <- lazyFlags) {
            val ref = new Var(propertyMetadata, entity, true)
            propertyMetadata.varField.set(entity, ref)
        }
    }

    private def hackPrevaylerToActAsARedoLogOnly = {
        val publisher = Reflection.get(prevayler, "_publisher").asInstanceOf[AbstractPublisher]
        val guard = Reflection.get(prevayler, "_guard").asInstanceOf[PrevalentSystemGuard[PrevaylerStorageSystem]]
        val journalSerializer = Reflection.get(prevayler, "_journalSerializer").asInstanceOf[Serializer]
        publisher.cancelSubscription(guard)
        val dummyCapsule = new DummyTransactionCapsule
        publisher.addSubscriber(new TransactionSubscriber {
            def receive(transactionTimestamp: TransactionTimestamp) = {
                guard.receive(
                    new TransactionTimestamp(
                        dummyCapsule,
                        transactionTimestamp.systemVersion,
                        transactionTimestamp.executionTime))
            }
        })
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
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {
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

        for ((entityId, changeSet) <- assignments)
            prevalentSystem += (entityId -> context.liveCache.materializeEntity(entityId))

        for (entityId <- deletes)
            prevalentSystem -= entityId

        None
    }

    protected[activate] def query(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] =
        List()

    override protected[activate] def migrateStorage(action: ModifyStorageAction): Unit =
        action match {
            case action: StorageRemoveTable =>
                val idsByEntityName = prevalentSystem.keys.toList.groupBy(id =>
                    EntityHelper.getEntityName(EntityHelper.getEntityClassFromId(id)))
                val idsToRemove = idsByEntityName.getOrElse(action.name, List())
                prevayler.execute(new PrevaylerMemoryStorageTransaction(context, new HashMap, new HashSet(idsToRemove)))
                PrevaylerMemoryStorageTransaction.destroyEntity(new HashSet(idsToRemove), context.liveCache)
                prevalentSystem --= idsToRemove
            case _ =>
        }

}

class PrevaylerMemoryStorageTransaction(
    val context: ActivateContext,
    val assignments: HashMap[String, HashMap[String, StorageValue]],
    val deletes: HashSet[String])
        extends PrevaylerTransaction[PrevaylerStorageSystem] {
    def executeOn(system: PrevaylerStorageSystem, date: java.util.Date) = {
        val liveCache = context.liveCache

        for ((entityId, changeSet) <- assignments)
            system += (entityId -> liveCache.materializeEntity(entityId))

        for (entityId <- deletes)
            system -= entityId

        for ((entityId, changeSet) <- assignments) {
            val entity = liveCache.materializeEntity(entityId)
            entity.setInitialized
            for ((varName, value) <- changeSet; if (varName != "id")) {
                val ref = entity.varNamed(varName)
                val entityValue = Marshaller.unmarshalling(value, ref.tval(None))
                ref.setRefContent(Option(liveCache.materialize(entityValue)))
            }
        }

        PrevaylerMemoryStorageTransaction.destroyEntity(deletes, liveCache)

    }
}

object PrevaylerMemoryStorageTransaction {
    def destroyEntity(entityIds: HashSet[String], liveCache: LiveCache) =
        for (entityId <- entityIds) {
            val entity = liveCache.materializeEntity(entityId)
            liveCache.delete(entityId)
            entity.setInitialized
            for (ref <- entity.vars)
                ref.destroyInternal
        }
}

object PrevaylerMemoryStorageFactory extends StorageFactory {
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        properties.get("prevalenceDirectory").map(new PrevaylerStorage(_)).getOrElse(new PrevaylerStorage())
}
