package net.fwbrasil.activate.storage.prevalent

import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.statement.query.Query
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.HashSet
import net.fwbrasil.activate.ActivateContext
import java.util.HashMap
import net.fwbrasil.activate.serialization.Serializer
import net.fwbrasil.activate.serialization.javaSerializer
import net.fwbrasil.activate.storage.marshalling.Marshaller
import java.nio.BufferUnderflowException
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.serialization.kryoSerializer
import java.io.FileOutputStream
import net.fwbrasil.activate.storage.SnapshotableStorage
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.util.Reflection._
import scala.collection.concurrent.TrieMap
import net.fwbrasil.activate.entity.EntityHelper

class PrevalentStorageSystem {
    val contents = new TrieMap[Class[Entity], TrieMap[Entity#ID, Entity]]
    def add(entity: Entity) =
        entitiesMapFor(entity.niceClass) += entity.id -> entity
    def remove(entityClass: Class[Entity], entityId: Entity#ID) =
        entitiesMapFor(entityClass) -= entityId
    def remove(entity: Entity): Unit =
        remove(entity.niceClass, entity.id)
    def entities =
        contents.values.map(_.values).flatten
        def entitiesListFor(name: String) = 
            contents.keys.filter(clazz => EntityHelper.getEntityName(clazz) == name)
            .map(contents(_).values)
            .flatten
    def entitiesMapFor(entityClass: Class[Entity]) = {
        contents.get(entityClass).getOrElse {
            this.synchronized {
                contents.getOrElseUpdate(entityClass, new TrieMap[Entity#ID, Entity])
            }
        }
    }
}

class PrevalentStorage(
    directory: String,
    serializer: Serializer = javaSerializer,
    fileSize: Int = 10 * 1000 * 1000,
    bufferPoolSize: Int = Runtime.getRuntime.availableProcessors)(implicit context: ActivateContext)
        extends MarshalStorage[PrevalentStorageSystem]
        with SnapshotableStorage[PrevalentStorageSystem] {

    private val directoryFile = new File(directory)
    directoryFile.mkdir
    private val journal = new PrevalentJournal(directoryFile, serializer, fileSize, bufferPoolSize)

    private var system = journal.recover

    def directAccess = system

    def isMemoryStorage = true
    def isSchemaless = true
    def isTransactional = true
    def supportsQueryJoin = true

    initialize

    protected[activate] def initialize = {}

    def snapshot =
        try {
            Entity.serializeUsingEvelope = false
            journal.takeSnapshot(system)
        } finally {
            Entity.serializeUsingEvelope = true
        }

    override protected[activate] def reinitialize = {
        system = journal.recover
        import scala.collection.JavaConversions._
        context.hidrateEntities(system.entities)
    }

    override protected[activate] def store(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        val transaction =
            createTransaction(insertList, updateList, deleteList)
        journal.add(transaction)
        updateSystem(insertList, deleteList)

        None
    }

    private def createTransaction(
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]) =
        new PrevalentTransaction(
            insertList.map(tuple => ((tuple._1.id, tuple._1.niceClass), tuple._2)).toArray,
            updateList.map(tuple => ((tuple._1.id, tuple._1.niceClass), tuple._2)).toArray,
            deleteList.map(tuple => (tuple._1.id, tuple._1.niceClass)).toArray)

    private def updateSystem(
        insertList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]) =
        system.synchronized {
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

    override protected[activate] def migrateStorage(action: ModifyStorageAction): Unit = {

    }

}

object PrevalentStorageFactory extends StorageFactory {
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new PrevalentStorage(properties("directory"))
}