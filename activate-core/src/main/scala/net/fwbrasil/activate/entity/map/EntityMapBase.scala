package net.fwbrasil.activate.entity.map

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import net.fwbrasil.activate.entity.IdVar
import scala.concurrent.Future
import net.fwbrasil.activate.entity.EntityValidation
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.smirror.SConstructor
import net.fwbrasil.smirror.SClass

trait EntityMapBase[E <: Entity, T <: EntityMapBase[E, T]] {

    import EntityMapBase._

    implicit val m: Manifest[E]
    implicit val context: ActivateContext

    def get[V](f: E => V) =
        values.get(keyFor(f)).asInstanceOf[Option[V]]

    def getOrElse[V](f: E => V)(default: V) =
        values.getOrElse(keyFor(f), default).asInstanceOf[V]

    def apply[V](f: E => V) =
        values(keyFor(f)).asInstanceOf[V]

    def put[V, V1 <: V](f: E => V)(value: V1): T

    def updateEntity(id: E#ID): E = tryUpdate(id).get

    def tryUpdate(id: E#ID): Option[E] =
        context.byId[E](id).map(updateEntity(_))

    def asyncCreateEntity(implicit ctx: TransactionalExecutionContext) =
        Future(createEntity)

    def asyncUpdateEntity(id: E#ID)(implicit ctx: TransactionalExecutionContext): Future[E] =
        asyncTryUpdate(id).map(_.get)

    def asyncTryUpdate(id: E#ID)(implicit ctx: TransactionalExecutionContext): Future[Option[E]] =
        context.asyncById[E](id).map(_.map(updateEntity(_)))

    def createEntityUsingConstructor =
        context.transactional(context.nested) {
            val constructors = entityMetadata.sClass.asInstanceOf[SClass[E]].constructors
            val availableProperties = this.values.keys.toSet
            val selected =
                constructors.filter(_.parameters.forall {
                    parameter =>
                        parameter.hasDefaultValue || availableProperties.contains(parameter.name)
                })
            val constructor = selected.onlyOne("More than one constructor found.")
            val values =
                constructor.parameters.map {
                    parameter =>
                        (parameter.name,
                            this.values.get(parameter.name)
                            .orElse(parameter.defaultValueOption).get)
                }
            val entity = constructor.invoke(values.map(_._2): _*)
            updateEntity(entity, this.values -- values.map(_._1))
        }

    def createEntity =
        context.transactional(context.nested) {
            val id = null//IdVar.generateId(entityClass)
            val entity = context.liveCache.createLazyEntity(entityClass, id)
            entity.setInitialized
            entity.setNotPersisted
            updateEntity(entity)
            context.liveCache.toCache(entityClass, entity)
            entity.invariants
            entity.initializeListeners
            entity
        }

    def updateEntity(entity: E): E =
        updateEntity(entity, values)

    def updateEntity(entity: E, values: Map[String, Any]) =
        context.transactional(context.nested) {
            try {
                EntityValidation.setThreadOptions(Set())
                for ((property, value) <- values) {
                    val ref = entity.varNamed(property)
                    if (ref == null)
                        throw new NullPointerException(s"Invalid property name $property for class ${m.runtimeClass}.")
                    if (ref.valueClass.isPrimitive && value == null)
                        throw new NullPointerException(s"Cant set null to a primitive property $property for class ${m.runtimeClass}.")
                    if (ref.isOptionalValue)
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

    def values: Map[String, Any]

    override def toString = getClass.getSimpleName + "(" + values.mkString(",") + ")"

    private val entityClass = erasureOf[E]
    private val entityMetadata = EntityHelper.getEntityMetadata(entityClass)

}

object EntityMapBase {

    private[activate] def varToValue(ref: Var[Any]) =
        if (ref.isOptionalValue)
            ref.get
        else
            ref.getValue

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