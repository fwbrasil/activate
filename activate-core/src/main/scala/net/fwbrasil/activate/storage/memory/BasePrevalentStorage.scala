package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.statement.query.Query
import java.io.File
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.entity.BaseEntity

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
            BaseEntity.serializeUsingEvelope = false
            snapshot(system)
        } finally {
            BaseEntity.serializeUsingEvelope = true
        }

    protected def snapshot(system: S): Unit
    protected def recover: S
    protected def logTransaction(
        insertList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
        updateList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
        deleteList: Array[(BaseEntity#ID, Class[BaseEntity])])

    override protected[activate] def reinitialize = {
        system = recover
        import scala.collection.JavaConversions._
        context.hidrateEntities(system.entities)
    }

    override protected[activate] def store(
        readList: List[(BaseEntity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(BaseEntity, Map[String, StorageValue])],
        updateList: List[(BaseEntity, Map[String, StorageValue])],
        deleteList: List[(BaseEntity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        logTransaction(
            insertList.map(tuple => ((tuple._1.id, tuple._1.niceClass), tuple._2)).toArray,
            updateList.map(tuple => ((tuple._1.id, tuple._1.niceClass), tuple._2)).toArray,
            deleteList.map(tuple => (tuple._1.id, tuple._1.niceClass)).toArray)
        updateSystem(insertList, deleteList)

        None
    }

    private def updateSystem(
        insertList: List[(BaseEntity, Map[String, StorageValue])],
        deleteList: List[(BaseEntity, Map[String, StorageValue])]) = {
        for ((entity, properties) <- insertList)
            system.add(entity)
        for ((entity, properties) <- deleteList)
            system.remove(entity)
    }

    protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[BaseEntity]]): List[List[StorageValue]] =
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