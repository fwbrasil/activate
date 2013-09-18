package net.fwbrasil.activate.storage.prevalent

import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.marshalling.Marshaller

class PrevalentTransaction(
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
                system.remove(entityId)
                for (ref <- entity.vars)
                    ref.destroyInternal
            }
        }
    }
    
}