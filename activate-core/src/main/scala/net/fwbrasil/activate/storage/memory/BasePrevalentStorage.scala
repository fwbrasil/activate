package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.Query
import java.io.File
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable

trait BasePrevalentStorage[S <: BasePrevalentStorageSystem, D]
        extends MarshalStorage[D]{

    protected implicit val context: ActivateContext

    protected var system: S = recover

    def isMemoryStorage = true
    def isSchemaless = true
    def isTransactional = true
    def supportsQueryJoin = true

    def snapshot: Unit =
        try {
            Entity.serializeUsingEvelope = false
            snapshot(system)
        } finally {
            Entity.serializeUsingEvelope = true
        }

    protected def snapshot(system: S): Unit
    protected def recover: S
    protected def logTransaction(
        insertList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
        updateList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
        deleteList: Array[(Entity#ID, Class[Entity])])

    override protected[activate] def reinitialize = {
        system = recover
        import scala.collection.JavaConversions._
        context.hidrateEntities(system.entities)
    }

    override protected[activate] def store(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        logTransaction(
            insertList.map(tuple => ((tuple._1.id, tuple._1.niceClass), tuple._2)).toArray,
            updateList.map(tuple => ((tuple._1.id, tuple._1.niceClass), tuple._2)).toArray,
            deleteList.map(tuple => (tuple._1.id, tuple._1.niceClass)).toArray)
        updateSystem(insertList, deleteList)

        None
    }

    private def updateSystem(
        insertList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]) = {
        for ((entity, properties) <- insertList)
            system.add(entity)
        for ((entity, properties) <- deleteList)
            system.remove(entity)
    }

    protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] =
        List()

    override protected[activate] def migrateStorage(action: ModifyStorageAction): Unit =
        action match {
            case action: StorageRemoveTable =>
                val idsToRemove = system.entitiesListFor(action.name).map(entity => (entity.id, entity.niceClass)).toList
                logTransaction(Array(), Array(), idsToRemove.toArray)
                for ((entityId, entityClass) <- idsToRemove) {
                    val entity = context.liveCache.materializeEntity(entityId, entityClass)
                    context.liveCache.delete(entityId)
                    entity.setInitialized
                    for (ref <- entity.vars)
                        ref.destroyInternal
                }
                for ((entityId, entityClass) <- idsToRemove)
                    system.remove(entityClass, entityId)
            case _ =>
        }

}