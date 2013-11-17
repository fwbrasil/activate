package net.fwbrasil.activate.storage.prevayler

import java.util.HashSet
import java.util.HashMap
import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
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
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.storage.SnapshotableStorage
import scala.collection.concurrent.TrieMap
import java.util.concurrent.ConcurrentHashMap

class PrevaylerStorageSystem extends Serializable {
    val contents = new ConcurrentHashMap[Class[Entity], ConcurrentHashMap[Entity#ID, Entity]]
    def add(entity: Entity) =
        entitiesMapFor(entity.niceClass) += entity.id -> entity
    def remove(entityClass: Class[Entity], entityId: Entity#ID) =
        entitiesMapFor(entityClass) -= entityId
    def entities =
        contents.values.map(_.values).flatten
    def entitiesListFor(name: String) =
        contents.keys.filter(clazz => EntityHelper.getEntityName(clazz) == name)
            .map(contents(_).values)
            .flatten
    def entitiesMapFor(entityClass: Class[Entity]) = {
        Option(contents.get(entityClass)).getOrElse {
            this.synchronized {
                contents.getOrElseUpdate(entityClass, new ConcurrentHashMap[Entity#ID, Entity])
            }
        }
    }
}

@implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
class PrevaylerStorage(
    val factory: PrevaylerFactory[PrevaylerStorageSystem])(implicit val context: ActivateContext)
        extends MarshalStorage[Prevayler[PrevaylerStorageSystem]]
        with SnapshotableStorage[Prevayler[PrevaylerStorageSystem]] {

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
        hackPrevaylerToActAsARedoLogOnly
        context.hidrateEntities(prevalentSystem.entities)
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
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {
        // Just ignore mass statements!
        val inserts =
            (for ((entity, propertyMap) <- insertList)
                yield ((entity.id, entity.niceClass) -> propertyMap.toList))
        val updates =
            (for ((entity, propertyMap) <- updateList)
                yield ((entity.id, entity.niceClass) -> propertyMap.toList))
        val deletes =
            for ((entity, propertyMap) <- deleteList)
                yield (entity.id, entity.niceClass)
        val assignments =
            new HashMap[(Entity#ID, Class[Entity]), HashMap[String, StorageValue]]((inserts ++ updates).toMap.mapValues(l => new HashMap[String, StorageValue](l.toMap)))

        prevayler.execute(new PrevaylerMemoryStorageTransaction(context, assignments, new HashSet(deletes)))

        for (((entityId, entityClass), changeSet) <- assignments)
            prevalentSystem.add(context.liveCache.materializeEntity(entityId, entityClass))

        for ((entityId, entityClass) <- deletes)
            prevalentSystem.remove(entityClass, entityId)

        None
    }

    protected[activate] def query(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] =
        List()

    override protected[activate] def migrateStorage(action: ModifyStorageAction): Unit =
        action match {
            case action: StorageRemoveTable =>
                val idsToRemove = prevalentSystem.entitiesListFor(action.name).map(entity => (entity.id, entity.niceClass)).toList
                prevayler.execute(new PrevaylerMemoryStorageTransaction(context, new HashMap, new HashSet(idsToRemove)))
                PrevaylerMemoryStorageTransaction.destroyEntity(new HashSet(idsToRemove), context.liveCache)
                for ((entityId, entityClass) <- idsToRemove)
                    prevalentSystem.remove(entityClass, entityId)
            case _ =>
        }

}

class PrevaylerMemoryStorageTransaction(
    val context: ActivateContext,
    val assignments: HashMap[(Entity#ID, Class[Entity]), HashMap[String, StorageValue]],
    val deletes: HashSet[(Entity#ID, Class[Entity])])
        extends PrevaylerTransaction[PrevaylerStorageSystem] {
    def executeOn(system: PrevaylerStorageSystem, date: java.util.Date) =
        context.transactional(context.transient) {
            val liveCache = context.liveCache

            for (((entityId, entityClass), changeSet) <- assignments) {
                val entity = liveCache.materializeEntity(entityId, entityClass)
                entity.setInitialized
                system.add(entity)
            }

            for ((entityId, entityClass) <- deletes)
                system.remove(entityClass, entityId)

            for (((entityId, entityClass), changeSet) <- assignments) {
                val entity = liveCache.materializeEntity(entityId, entityClass)
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
    def destroyEntity(entityIds: HashSet[(Entity#ID, Class[Entity])], liveCache: LiveCache) =
        for ((entityId, entityClass) <- entityIds) {
            val entity = liveCache.materializeEntity(entityId, entityClass)
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
