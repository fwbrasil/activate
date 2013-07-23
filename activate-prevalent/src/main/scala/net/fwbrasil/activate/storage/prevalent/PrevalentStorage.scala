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

class PrevalentStorage(file: File, serializer: Serializer = javaSerializer)(implicit context: ActivateContext)
        extends MarshalStorage[PrevalentStorageSystem] {

    private val system = new PrevalentStorageSystem // recover from file
    private val channel = new RandomAccessFile(file, "rw").getChannel
    private val buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, 2000 * 1024 * 1024)

    def directAccess = system

    def isMemoryStorage = true
    def isSchemaless = true
    def isTransactional = true
    def supportsQueryJoin = true

    initialize

    protected[activate] def initialize = {
        system.clear
        buffer.rewind
        buffer.get // why there is this empty byte?
        while (bufferHasTransactionToRecover) {
            val transactionSize = buffer.getInt
            val bytes = new Array[Byte](transactionSize)
            buffer.get(bytes)
            val transaction = serializer.fromSerialized[PrevalentStorageTransaction](bytes)
            transaction.recover(system)
        }
    }

    private def bufferHasTransactionToRecover =
        try {
            buffer.mark
            val i = buffer.getInt
            i > 0
        } catch {
            case e: BufferUnderflowException =>
                false
        } finally {
            buffer.reset
        }

    override protected[activate] def reinitialize =
        initialize

    override protected[activate] def store(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        val transaction = createTransaction(insertList, updateList, deleteList)
        val bytes = serializer.toSerialized(transaction)
        writeToBuffer(bytes)
        updateSystem(insertList, deleteList)

        None
    }
    
    private def writeToBuffer(bytes: Array[Byte]) = 
        buffer.synchronized {
            buffer.putInt(bytes.length)
            buffer.put(bytes)
        }

    private def createTransaction(
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]) =
        new PrevalentStorageTransaction(
            insertList.map(tuple => (tuple._1.id, tuple._2)).toArray,
            updateList.map(tuple => (tuple._1.id, tuple._2)).toArray,
            deleteList.map(_._1.id).toArray)

    private def updateSystem(
        insertList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]) = {

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

class PrevalentStorageTransaction(
    val insertList: Array[(String, Map[String, StorageValue])],
    val updateList: Array[(String, Map[String, StorageValue])],
    val deleteList: Array[String])
        extends Serializable {

    def recover(system: PrevalentStorageSystem)(implicit context: ActivateContext) = {
        import context._
        transactional {
            for ((entityId, changeSet) <- insertList ++ updateList) {
                val entity = liveCache.materializeEntity(entityId)
                entity.setInitialized
                system.put(entityId, entity)
                for ((varName, value) <- changeSet; if (varName != "id")) {
                    val ref = entity.varNamed(varName)
                    val entityValue = Marshaller.unmarshalling(value, ref.tval(None))
                    ref.setRefContent(Option(liveCache.materialize(entityValue)))
                }
            }
            for (entityId <- deleteList) {
                val entity = liveCache.materializeEntity(entityId)
                entity.setInitialized
                liveCache.delete(entity)
                for (ref <- entity.vars)
                    ref.destroyInternal
            }
        }
    }

}