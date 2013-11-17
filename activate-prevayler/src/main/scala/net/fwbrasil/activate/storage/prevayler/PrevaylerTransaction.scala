package net.fwbrasil.activate.storage.prevayler

import org.prevayler.Transaction
import net.fwbrasil.activate.storage.memory.BasePrevalentTransaction
import net.fwbrasil.activate.storage.memory.BasePrevalentStorageSystem
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.ActivateContext
import java.util.Date

class PrevaylerTransaction(
    context: ActivateContext,
    insertList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
    updateList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
    deleteList: Array[(Entity#ID, Class[Entity])])
        extends BasePrevalentTransaction(
            context,
            insertList,
            updateList,
            deleteList)
        with Transaction[BasePrevalentStorageSystem] {
    
    override def executeOn(system: BasePrevalentStorageSystem, date: Date) =
        super.execute(system)
}