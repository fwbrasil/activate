package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.entity.Entity

class BasePrevalentTransaction(
    val context: ActivateContext,
    val insertList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
    val updateList: Array[((Entity#ID, Class[Entity]), Map[String, StorageValue])],
    val deleteList: Array[(Entity#ID, Class[Entity])])
        extends Serializable {

    def execute(system: BasePrevalentStorageSystem) = {
        import context._
        transactional(transient) {

            val assignments = insertList ++ updateList

            for (((entityId, entityClass), changeSet) <- assignments) 
                system.add(materialize(entityId, entityClass))

            for ((entityId, entityClass) <- deleteList) {
                val entity = materialize(entityId, entityClass)
                liveCache.remove(entity)
                system.remove(entityClass, entityId)
                for (ref <- entity.vars)
                    ref.destroyInternal
            }

            for (((entityId, entityClass), changeSet) <- assignments) {
                val entity = materialize(entityId, entityClass)
                system.add(entity)
                for ((varName, value) <- changeSet; if (varName != "id")) {
                    val ref = entity.varNamed(varName)
                    val entityValue = Marshaller.unmarshalling(value, ref.tval(None))
                    ref.setRefContent(Option(liveCache.materialize(entityValue)))
                }
            }
        }
    }

    private def materialize(entityId: Entity#ID, entityClass: Class[Entity]) = {
        val entity = context.liveCache.materializeEntity(entityId, entityClass)
        entity.setInitialized
        entity
    }

}