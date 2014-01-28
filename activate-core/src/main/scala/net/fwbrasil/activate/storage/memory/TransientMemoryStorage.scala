package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.BaseEntity
import scala.collection.mutable.{ HashMap => MutableHashMap }
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import scala.collection.mutable.HashSet
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.TransactionHandle
import scala.collection.concurrent.TrieMap

class TransientMemoryStorage extends Storage[MutableHashMap[Class[_ <: BaseEntity], TrieMap[BaseEntity#ID, BaseEntity]]] {

    private val storageMap = MutableHashMap[Class[_ <: BaseEntity], TrieMap[BaseEntity#ID, BaseEntity]]()

    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = true

    override def supportsAsync = true

    def directAccess =
        storageMap

    override def reinitialize =
        for (entity <- storageMap.values.map(_.values).flatten)
            entity.addToLiveCache

    override def toStorage(
        readList: List[(BaseEntity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(BaseEntity, Map[String, EntityValue[Any]])],
        updateList: List[(BaseEntity, Map[String, EntityValue[Any]])],
        deleteList: List[(BaseEntity, Map[String, EntityValue[Any]])]): Option[TransactionHandle] = {

        for ((entity, properties) <- insertList)
            entityClassMap(entity) += entity.id -> entity
        for ((entity, properties) <- deleteList)
            entityClassMap(entity) -= entity.id

        None
    }
    
    private def entityClassMap(entity: BaseEntity) =
        storageMap.synchronized {
            storageMap.getOrElseUpdate(entity.getClass, new TrieMap)
        }

    override def fromStorage(query: Query[_], entitiesReadFromCache: List[List[BaseEntity]]): List[List[EntityValue[_]]] =
        List()

    override def isMemoryStorage = true

    override def migrate(action: StorageAction): Unit = {}

}

object TransientMemoryStorageFactory extends StorageFactory {
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new TransientMemoryStorage
}