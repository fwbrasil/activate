package net.fwbrasil.activate.storage.prevalent

import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.entity.Entity

class PrevalentTransaction(
    val insertList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
    val updateList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
    val deleteList: Array[(Entity#ID, Class[Entity])])
        extends Serializable {

    def recover(system: PrevalentStorageSystem)(implicit context: ActivateContext) = {
        import context._
        transactional {
            for (((entityId, entityClass), changeSet) <- insertList ++ updateList) {
                val entity = liveCache.materializeEntity(entityId, entityClass)
                entity.setInitialized
                system.add(entity)
                for ((varName, value) <- changeSet; if (varName != "id")) {
                    val ref = entity.varNamed(varName)
                    val entityValue = Marshaller.unmarshalling(value, ref.tval(None))
                    ref.setRefContent(Option(liveCache.materialize(entityValue)))
                }
            }
            for ((entityId, entityClass) <- deleteList) {
                val entity = liveCache.materializeEntity(entityId, entityClass)
                entity.setInitialized
                liveCache.delete(entity)
                system.remove(entityClass, entityId)
                for (ref <- entity.vars)
                    ref.destroyInternal
            }
        }
    }
    
}