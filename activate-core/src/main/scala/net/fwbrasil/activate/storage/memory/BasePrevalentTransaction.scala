package net.fwbrasil.activate.storage.memory

import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.entity.BaseEntity

class BasePrevalentTransaction(
    val context: ActivateContext,
    val insertList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
    val updateList: Array[((BaseEntity#ID, Class[BaseEntity]), Map[String, StorageValue])],
    val deleteList: Array[(BaseEntity#ID, Class[BaseEntity])])
        extends Serializable {

    def execute(system: BasePrevalentStorageSystem) = {
        import context._
        transactional(transient) {

            for (((entityId, entityClass), changeSet) <- insertList) 
                system.add(materialize(entityId, entityClass))

            for ((entityId, entityClass) <- deleteList) {
                val entity = system.entitiesMapFor(entityClass).get(entityId)
                liveCache.remove(entity)
                system.remove(entityClass, entityId)
                for (ref <- entity.vars)
                    ref.destroyInternal
            }
            
            val assignments = insertList ++ updateList

            for (((entityId, entityClass), changeSet) <- assignments) {
                val entity = system.entitiesMapFor(entityClass).get(entityId)
                for ((varName, value) <- changeSet; if (varName != "id")) {
                    val ref = entity.varNamed(varName)
                    val entityValue = Marshaller.unmarshalling(value, ref.tval(None))
                    ref.setRefContent(Option(liveCache.materialize(entityValue)))
                }
            }
        }
    }

    private def materialize(entityId: BaseEntity#ID, entityClass: Class[BaseEntity]) = {
        val entity = context.liveCache.materializeEntity(entityId, entityClass)
        entity.setInitialized
        entity
    }

}