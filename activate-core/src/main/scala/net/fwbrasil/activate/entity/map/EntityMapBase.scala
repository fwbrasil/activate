package net.fwbrasil.activate.entity.map

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.entity.BaseEntity
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
import net.fwbrasil.activate.entity.EntityPropertyMetadata

trait EntityMapBase[E <: BaseEntity, T <: EntityMapBase[E, T]] {

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
            val constructor =
                selected.onlyOne(
                    "There should be one and only one constructor available for the " +
                        s"entity map values. \nCompatible constructors: $selected. \nAvailable constructors: constructors. \nEntity map values: $this")
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
            val id = context.nextIdFor(entityClass)
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
                for ((property, value) <- values if (property != "id")) {
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
    private val properties = entityMetadata.propertiesMetadata.groupBy(_.name).mapValues(_.head)

    protected def verifyValuesTypes =
        for ((propertyName, value) <- values)
            try {
                val property = propertyNamed(propertyName)
                val propertyType = typeFor(property.propertyType)
                if (property.isOption)
                    value match {
                        case None =>
                        case Some(value) =>
                            propertyType.cast(value)
                        case other =>
                            classOf[Option[Any]].cast(other)
                    }
                else
                    propertyType.cast(value)
            } catch {
                case e: ClassCastException =>
                    throw new IllegalStateException(s"Invalid value '$value' for property '$propertyName'", e)
            }

    private def propertyNamed(propertyName: String) =
        properties.get(propertyName)
            .getOrElse(throw new IllegalStateException(s"Invalid property $propertyName. $this"))

    private def typeFor(clazz: Class[_]) =
        if (clazz == classOf[Int])
            classOf[java.lang.Integer]
        else if (clazz == classOf[Long])
            classOf[java.lang.Long]
        else if (clazz == classOf[Float])
            classOf[java.lang.Float]
        else if (clazz == classOf[Double])
            classOf[java.lang.Double]
        else if (clazz == classOf[Char])
            classOf[java.lang.Character]
        else if (clazz == classOf[Boolean])
            classOf[java.lang.Boolean]
        else
            clazz

}

trait EntityMapContext {
    this: ActivateContext =>
    implicit class EntityToMap[E <: BaseEntity: Manifest](entity: E) {
        def toMap = new EntityMap(entity)
        def toMutableMap = new MutableEntityMap(entity)
    }
}

object EntityMapBase {

    private[activate] def varToValue(ref: Var[Any]) =
        if (ref.isOptionalValue)
            ref.get
        else
            ref.getValue

    private[activate] def mock[E <: BaseEntity: Manifest] =
        StatementMocks.mockEntity(erasureOf[E]).asInstanceOf[E]

    private[activate] def lastVarNameCalled =
        StatementMocks.lastFakeVarCalled.get.name

    private[activate] def keyFor[E <: BaseEntity: Manifest](f: E => Any) = {
        f(mock)
        lastVarNameCalled
    }

    private[activate] def keyAndValueFor[E <: BaseEntity: Manifest](f: E => (_, _)) = {
        val value = f(mock)
        (lastVarNameCalled, value._2)
    }

}