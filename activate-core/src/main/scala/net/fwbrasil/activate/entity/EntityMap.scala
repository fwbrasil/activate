package net.fwbrasil.activate.entity

import scala.language._
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import scala.concurrent.Future

class EntityMap[E <: Entity] private[activate] (private var values: Map[String, Any])(implicit m: Manifest[E], context: ActivateContext) {

    import EntityMap._

    def this(entity: E)(implicit m: Manifest[E], context: ActivateContext) =
        this(entity.vars.map(ref => (ref.name, ref.getValue)).toMap)

    def this(init: ((E) => (_, _))*)(implicit m: Manifest[E], context: ActivateContext) =
        this(init.map(EntityMap.keyAndValueFor[E](_)(m)).toMap)

    def get[V](f: E => V) =
        values.get(keyFor(f)).asInstanceOf[Option[V]]

    def getOrElse[V](f: E => V, default: V) =
        values.getOrElse(keyFor(f), default).asInstanceOf[V]

    def apply[V](f: E => V) =
        values(keyFor(f)).asInstanceOf[V]

    def put[V, V1 <: V](f: E => V)(value: V1) =
        values += keyFor(f) -> value

    def updateEntity(id: String): E = tryUpdate(id).get

    def tryUpdate(id: String): Option[E] =
        context.byId[E](id).map(updateEntity)

    def asyncCreateEntity(implicit ctx: TransactionalExecutionContext) =
        Future(createEntity)

    def asyncUpdateEntity(id: String)(implicit ctx: TransactionalExecutionContext): Future[E] =
        asyncTryUpdate(id).map(_.get)

    def asyncTryUpdate(id: String)(implicit ctx: TransactionalExecutionContext): Future[Option[E]] =
        context.asyncById[E](id).map(_.map(updateEntity))

    def createEntity = {
        val entityClass = erasureOf[E]
        val id = IdVar.generateId(entityClass)
        val entity = context.liveCache.createLazyEntity(entityClass, id)
        entity.setInitialized
        entity.setNotPersisted
        updateEntity(entity)
        context.liveCache.toCache(entityClass, entity)
        entity
    }

    protected def updateEntity(entity: E) = {
        try {
            EntityValidation.setThreadOptions(Set())
            val entityMetadata = EntityHelper.getEntityMetadata(erasureOf[E])
            val metadatasMap =
                entityMetadata.propertiesMetadata
                    .groupBy(_.name)
                    .mapValues(_.head)
            for ((property, value) <- values) {
                val ref = entity.varNamed(property)
                val propertyMetadata = metadatasMap(property)
                if (propertyMetadata.isOption)
                    ref.put(value.asInstanceOf[Option[_]])
                else
                    ref.putValue(value)
            }
        } finally {
        	EntityValidation.setThreadOptions(EntityValidation.getGlobalOptions)
        	entity.validate
        }
        entity
    }

}

object EntityMap {

    def apply[E <: Entity](init: ((E) => (_, _))*)(implicit m: Manifest[E], context: ActivateContext): EntityMap[E] =
        new EntityMap(init: _*)

    def forEntity[E <: Entity](entity: E)(implicit m: Manifest[E], context: ActivateContext) =
        new EntityMap(entity)

    private[activate] def mock[E <: Entity: Manifest] =
        StatementMocks.mockEntity(erasureOf[E]).asInstanceOf[E]

    private[activate] def lastVarNameCalled =
        StatementMocks.lastFakeVarCalled.get.name

    private[activate] def keyFor[E <: Entity: Manifest](f: E => Any) = {
        f(mock)
        lastVarNameCalled
    }

    private[activate] def keyAndValueFor[E <: Entity: Manifest](f: E => (_, _)) = {
        val value = f(mock)
        (lastVarNameCalled, value._2)
    }

}

