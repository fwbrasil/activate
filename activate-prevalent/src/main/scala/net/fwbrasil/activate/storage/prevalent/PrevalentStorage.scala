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
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConversions._
import net.fwbrasil.activate.storage.memory.BasePrevalentTransaction
import net.fwbrasil.activate.storage.memory.BasePrevalentStorageSystem
import net.fwbrasil.activate.storage.memory.BasePrevalentStorage

class PrevalentStorage(
    val directory: String,
    val serializer: Serializer = javaSerializer,
    val fileSize: Int = 10 * 1000 * 1000,
    val bufferPoolSize: Int = Runtime.getRuntime.availableProcessors)(implicit val context: ActivateContext)
        extends BasePrevalentStorage[BasePrevalentStorageSystem, BasePrevalentStorageSystem] {

    private var journal: PrevalentJournal = _

    def directAccess = system

    override protected def snapshot(system: BasePrevalentStorageSystem) =
        journal.takeSnapshot(system)

    override protected def recover = {
        if (journal == null) {
            val directoryFile = new File(directory)
            directoryFile.mkdir
            journal = new PrevalentJournal(directoryFile, serializer, fileSize, bufferPoolSize)
        }
        journal.recover
    }

    override protected def logTransaction(
        insertList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
        updateList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
        deleteList: Array[(Entity#ID, Class[Entity])]) = {
        val transaction = new BasePrevalentTransaction(context, insertList, updateList, deleteList)
        journal.add(transaction)
    }

}

object PrevalentStorageFactory extends StorageFactory {
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new PrevalentStorage(properties("directory"))
}