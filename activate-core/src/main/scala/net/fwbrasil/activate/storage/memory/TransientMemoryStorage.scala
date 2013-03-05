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
import scala.collection.mutable.SynchronizedSet
import scala.collection.mutable.HashSet
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.TransactionHandle

class TransientMemoryStorageSet extends HashSet[Entity] with SynchronizedSet[Entity] {
    override def elemHashCode(key: Entity) = java.lang.System.identityHashCode(key)
}

class TransientMemoryStorage extends Storage[HashSet[Entity]] {

    val storageSet = new TransientMemoryStorageSet

    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = true

    def directAccess =
        storageSet

    override def reinitialize =
        for (entity <- storageSet)
            entity.addToLiveCache

    override def toStorage(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])]): Option[TransactionHandle] = {

        for ((entity, properties) <- insertList)
            storageSet += entity
        for ((entity, properties) <- deleteList)
            storageSet -= entity

        None
    }

    override def fromStorage(query: Query[_]): List[List[EntityValue[_]]] =
        List()

    override def isMemoryStorage = true

    override def migrate(action: StorageAction): Unit = {}

}

object TransientMemoryStorageFactory extends StorageFactory {
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new TransientMemoryStorage
}