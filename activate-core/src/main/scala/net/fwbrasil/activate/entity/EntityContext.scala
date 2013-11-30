package net.fwbrasil.activate.entity

import net.fwbrasil.radon.transaction.TransactionContext
import net.fwbrasil.activate.entity.id.EntityIdContext
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.cache.CacheType
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.map.EntityMapContext
import net.fwbrasil.activate.cache.CustomCache
import net.fwbrasil.activate.entity.id.CustomID
import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.util.Reflection._

trait EntityContext
        extends ValueContext
        with TransactionContext
        with LazyListContext
        with EntityIdContext
        with EntityMapContext {
    this: ActivateContext =>

    type Alias = net.fwbrasil.activate.entity.InternalAlias @scala.annotation.meta.field
    type Var[A] = net.fwbrasil.activate.entity.Var[A]
    type EntityMap[E <: BaseEntity] = net.fwbrasil.activate.entity.map.EntityMap[E]
    type MutableEntityMap[E <: BaseEntity] = net.fwbrasil.activate.entity.map.MutableEntityMap[E]
    type Encoder[A, B] = net.fwbrasil.activate.entity.Encoder[A, B]

    type Entity = net.fwbrasil.activate.entity.Entity
    type EntityWithCustomID[ID] = net.fwbrasil.activate.entity.EntityWithCustomID[ID]

    protected def liveCacheType = CacheType.softReferences

    protected def customCaches: List[CustomCache[_]] = List()

    protected[activate] val liveCache =
        new LiveCache(this, liveCacheType, customCaches.asInstanceOf[List[CustomCache[BaseEntity]]])

    protected[activate] def entityMaterialized(entity: BaseEntity) = {}

    protected[activate] def hidrateEntities(entities: Iterable[BaseEntity])(implicit context: ActivateContext) =
        for (entity <- entities) {
            initializeBitmaps(entity)
            entity.invariants
            entity.initializeListeners
            context.transactional(context.transient) {
                initializeLazyFlags(entity)
            }
            context.liveCache.toCache(entity)
        }

    private def initializeLazyFlags(entity: net.fwbrasil.activate.entity.BaseEntity): Unit = {
        val metadata = EntityHelper.getEntityMetadata(entity.getClass)
        val lazyFlags = metadata.propertiesMetadata.filter(p => p.isLazyFlag && p.isTransient)
        for (propertyMetadata <- lazyFlags) {
            val ref = new Var(propertyMetadata, entity, true)
            propertyMetadata.varField.set(entity, ref)
        }
    }

}