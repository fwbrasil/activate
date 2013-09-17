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
        expiration: Duration = Duration.Inf)(implicit ctx: ActivateContext) {

    private val cache = cacheBuilder.build[String, E]

    def entityClass = erasureOf[E]

    def add(entity: E) =
        if (entity.getClass.isAssignableFrom(erasureOf[E]) && satifyCondition(entity))
            cache.put(entity.id, entity)

    def clear =
        cache.invalidateAll

    def remove(entity: E): Unit =
        remove(entity.id)

    def remove(entityId: String) =
        cache.invalidate(entityId)

    private def satifyCondition(entity: E) =
        if (transactionalCondition) {
            val transaction =
                ctx.transactionManager
                    .getActiveTransaction
                    .map(new NestedTransaction(_))
                    .getOrElse(new Transaction)
            ctx.transactional(transaction) {
                condition(entity)
            }
        } else
            condition(entity)

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