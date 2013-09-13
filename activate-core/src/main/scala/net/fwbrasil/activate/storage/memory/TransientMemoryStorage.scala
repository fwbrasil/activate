package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
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

class TransientMemoryStorage extends Storage[TrieMap[String, Entity]] {

    private val storageMap = new TrieMap[String, Entity]

    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = true
    
    override def supportsAsync = true

    def directAccess =
        storageMap

    override def reinitialize =
        for (entity <- storageMap.values)
            entity.addToLiveCache

    override def toStorage(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])]): Option[TransactionHandle] = {

        for ((entity, properties) <- insertList)
            storageMap += entity.id -> entity
        for ((entity, properties) <- deleteList)
            storageMap -= entity.id

        None
    }

    override def fromStorage(query: Query[_], entitiesReadFromCache: List[List[Entity]]): List[List[EntityValue[_]]] =
        List()

    override def isMemoryStorage = true

    override def migrate(action: StorageAction): Unit = {}

}

object TransientMemoryStorageFactory extends StorageFactory {
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new TransientMemoryStorage
}