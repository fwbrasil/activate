package net.fwbrasil.activate.storage.prevayler

import org.prevayler.Transaction
import net.fwbrasil.activate.storage.memory.BasePrevalentTransaction
import net.fwbrasil.activate.storage.memory.BasePrevalentStorageSystem
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.ActivateContext
import java.util.Date

class PrevaylerTransaction(
    context: ActivateContext,
    insertList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
    updateList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
    deleteList: Array[(BaseEntity#ID, Class[BaseEntity])])
        extends BasePrevalentTransaction(
            context,
            insertList,
            updateList,
            deleteList)
        with Transaction[BasePrevalentStorageSystem] {
    
    override def executeOn(system: BasePrevalentStorageSystem, date: Date) =
        super.execute(system)
}