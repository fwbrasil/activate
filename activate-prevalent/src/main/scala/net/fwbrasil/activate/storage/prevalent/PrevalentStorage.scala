package net.fwbrasil.activate.storage.prevalent

import java.io.File

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.serialization.Serializer
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.memory.BasePrevalentStorage
import net.fwbrasil.activate.storage.memory.BasePrevalentStorageSystem
import net.fwbrasil.activate.storage.memory.BasePrevalentTransaction
import net.fwbrasil.activate.serialization.javaSerializer

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
        insertList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
        updateList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
        deleteList: Array[(BaseEntity#ID, Class[BaseEntity])]) = {
        val transaction = new BasePrevalentTransaction(context, insertList, updateList, deleteList)
        journal.add(transaction)
    }

}

object PrevalentStorageFactory extends StorageFactory {
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new PrevalentStorage(properties("directory"))
}