package net.fwbrasil.activate.cache

import net.fwbrasil.activate.entity.Entity
import CacheType._
import com.google.common.collect.MapMaker
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.radon.transaction.NestedTransaction
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction

case class CustomCache[E <: Entity: Manifest](
        cacheType: CacheType,
        transactionalCondition: Boolean = false,
        condition: E => Boolean = (e: E) => true,
        limitOption: Option[Long] = None,
        expiration: Duration = Duration.Inf) {

    private val cache = cacheBuilder.build[AnyRef, E]

    def entityClass = erasureOf[E]

    def add(entity: E)(implicit ctx: ActivateContext) =
        if (erasureOf[E].isAssignableFrom(entity.getClass) && satifyCondition(entity))
            cache.put(entity.id.asInstanceOf[AnyRef], entity)

    def clear =
        cache.invalidateAll

    def remove(entity: E): Unit =
        remove(entity.id)

    def remove(entityId: Entity#ID) =
        cache.invalidate(entityId)

    private def satifyCondition(entity: E)(implicit ctx: ActivateContext) =
        if (transactionalCondition && ctx.transactionManager.getActiveTransaction.isEmpty) {
            val transaction = new Transaction
            try
                ctx.transactional(transaction) {
                    tryCondition(entity)
                }
            finally
                transaction.rollback
        } else
            tryCondition(entity)

    private def tryCondition(entity: E) =
        try condition(entity)
        catch {
            case e: Throwable =>
                false
        }

    private def cacheBuilder = {
        var builder = cacheType.cacheBuilder
        if (expiration.isFinite) {
            builder = builder.expireAfterAccess(expiration.toSeconds, TimeUnit.SECONDS)
        }
        limitOption.map { limit =>
            builder = builder.maximumSize(limit)
        }
        builder
    }
}