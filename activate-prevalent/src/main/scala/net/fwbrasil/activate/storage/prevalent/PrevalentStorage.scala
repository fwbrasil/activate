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
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.serialization.kryoSerializer

class PrevalentStorageSystem extends HashMap[String, Entity]

class PrevalentStorage(
        directory: String, 
        serializer: Serializer = javaSerializer, 
        fileSize: Int = 10 * 1000 * 1000, 
        bufferPoolSize: Int = Runtime.getRuntime.availableProcessors)(implicit context: ActivateContext)
        extends MarshalStorage[PrevalentStorageSystem] {
    
    private val system = new PrevalentStorageSystem
    private val directoryFile = new File(directory)
    directoryFile.mkdir
    private val journal = new PrevalentJournal(directoryFile, serializer, fileSize, bufferPoolSize)

    def directAccess = system

    def isMemoryStorage = true
    def isSchemaless = true
    def isTransactional = true
    def supportsQueryJoin = true

    initialize

    protected[activate] def initialize = {
        system.clear
        journal.recover(system)
    }

    override protected[activate] def reinitialize =
        initialize

    override protected[activate] def store(
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
            insertList.map(tuple => (tuple._1.id, tuple._2)).toArray,
            updateList.map(tuple => (tuple._1.id, tuple._2)).toArray,
            deleteList.map(_._1.id).toArray)

    private def updateSystem(
        insertList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]) =
        system.synchronized {
            for ((entity, properties) <- insertList)
                system.put(entity.id, entity)
            for ((entity, properties) <- deleteList)
                system.remove(entity.id)
        }

    protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] =
        List()

    override protected[activate] def migrateStorage(action: ModifyStorageAction): Unit = {

    }

}