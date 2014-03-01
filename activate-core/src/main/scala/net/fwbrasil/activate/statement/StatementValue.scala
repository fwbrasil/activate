package net.fwbrasil.activate.statement

import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.ValueContext
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.statement.StatementMocks._
import net.fwbrasil.activate.util.ManifestUtil._
import scala.annotation.implicitNotFound
import net.fwbrasil.activate.statement.query.EagerQueryContext
import net.fwbrasil.activate.storage.marshalling.Marshaller

class StatementValue()

abstract class StatementSelectValue() extends StatementValue {
    def entityValue: EntityValue[_]
}

trait StatementValueContext extends ValueContext {

    private[fwbrasil] def toStatementValueRef[V](ref: Var[V]): StatementSelectValue = {
        val (entity, path) = propertyPath(ref)
        val sourceOption = From.entitySourceFor(entity)
        if (sourceOption.isDefined)
            new StatementEntitySourcePropertyValue(sourceOption.get, path)
        else new SimpleValue[V](() => ref.get.get, ref.tval)
    }

    private[activate] def propertyPath(ref: Var[_]) = {
        ref match {
            case ref: FakeVar[_] => {
                var propertyPath = List[Var[_]](ref)
                var entity = ref.outerEntity
                var originVar: FakeVar[_] = ref.originVar
                while (originVar != null) {
                    propertyPath ::= originVar
                    entity = originVar.outerEntity
                    originVar = originVar.originVar
                }
                (entity, propertyPath)
            }
            case ref: Var[_] =>
                (null, Nil)
        }
    }

    private def toStatementValueEntity[E <: BaseEntity](fEntity: () => E): StatementEntityValue[E] = {
        val entity = fEntity()
        val sourceOption = From.entitySourceFor(entity)
        if (sourceOption.isDefined)
            new StatementEntitySourceValue(sourceOption.get)
        else
            new StatementEntityInstanceValue(fEntity)
    }

    import language.implicitConversions
    
    implicit def toStatementValueEntityId(entityId: => BaseEntity#ID): StatementSelectValue =
        toStatementValueEntityValue(entityId)(manifestClass(entityId.getClass), EntityValue.tvalFunction(entityId.getClass, classOf[Object]))

    @implicitNotFound("Conversion to EntityValue not found. Perhaps the entity property is not supported.")
    implicit def toStatementValueEntityValue[V: Manifest](value: => V)(implicit m: Option[V] => EntityValue[V]): StatementSelectValue =
        toStatementValueEntityValueOption(
            if (value.isInstanceOf[Option[_]])
                value.asInstanceOf[Option[V]]
            else
                Option(value))

    @implicitNotFound("Conversion to EntityValue not found. Perhaps the entity property is not supported.")
    implicit def toStatementValueEntityValueOption[V: Manifest](value: => Option[V])(implicit m: Option[V] => EntityValue[V]): StatementSelectValue = {
        // Just to evaluate
        value
        StatementMocks.lastFakeVarCalled match {
            case Some(ref: Var[V]) =>
                toStatementValueRef(ref)
            case other =>
                value.getOrElse(null.asInstanceOf[V]) match {
                    case function: FunctionApply[V] =>
                        function
                    case entity: BaseEntity =>
                        toStatementValueEntity(() => value.getOrElse(null.asInstanceOf[V]).asInstanceOf[BaseEntity]).asInstanceOf[StatementSelectValue]
                    case other =>
                        val tval = EntityValue.tvalFunctionOption[V](erasureOf[V], classOf[Object]).getOrElse(m)
                        new SimpleValue[V](() => value.getOrElse(null.asInstanceOf[V]), tval)
                }
        }
    }

}

abstract class StatementEntityValue[V]() extends StatementSelectValue

case class StatementEntityInstanceValue[E <: BaseEntity](val fEntity: () => E) extends StatementEntityValue[E] {
    def entity = fEntity()
    def entityId = entity.id
    def storageValue = Marshaller.marshalling(entityValue)
    override def entityValue = EntityInstanceEntityValue[E](Option(entity))(manifestClass[E](entity.getClass))
    override def toString = entityId.toString
    override def hashCode = System.identityHashCode(this)
}

class StatementEntitySourceValue[V](val entitySource: EntitySource, val eager: Boolean = EagerQueryContext.isEager) extends StatementEntityValue[V] with Product {

    override def entityValue: EntityValue[_] = EntityInstanceEntityValue(None)(manifestClass(entitySource.entityClass))
    override def toString = entitySource.name + (if (eager) ".eager" else "")

    def entityClass = entitySource.entityClass
    def explodedSelectValues =
        StatementMocks.mockEntity(entityClass)
            .vars.filter(p => !p.isTransient)
            .map(propertyFor).toList
            
    def propertyFor(ref: Var[_]) =
        new StatementEntitySourcePropertyValue(entitySource, List(ref), false)

    def productElement(n: Int): Any =
        n match {
            case 0 => entitySource
            case 1 => eager
        }
    def productArity: Int = 2
    def canEqual(that: Any): Boolean =
        that.getClass == classOf[StatementEntitySourceValue[V]]
    override def equals(that: Any): Boolean =
        canEqual(that) &&
            that.asInstanceOf[StatementEntitySourceValue[V]].entitySource == entitySource &&
            that.asInstanceOf[StatementEntitySourceValue[V]].eager == eager
}

class StatementEntitySourcePropertyValue(override val entitySource: EntitySource, val propertyPathVars: List[Var[_]], pEager: Boolean = EagerQueryContext.isEager)
        extends StatementEntitySourceValue(entitySource, pEager) {
    
    propertyPathVars.find(_.isTransient).map {
        _ => throw new UnsupportedOperationException("Transient values can't be used for queries.")
    }
    
    override def entityClass = propertyPathVars.last.valueClass.asInstanceOf[Class[BaseEntity]]
    def lastVar = propertyPathVars.last
    def propertyPathNames =
        for (prop <- propertyPathVars)
            yield prop.name
    override def entityValue: EntityValue[_] = lastVar.asInstanceOf[StatementMocks.FakeVar[_]].tval(None).asInstanceOf[EntityValue[_]]
    override def toString = entitySource.name + "." + propertyPathNames.mkString(".") + (if (eager) ".eager" else "")

    override def propertyFor(ref: Var[_]) =
        new StatementEntitySourcePropertyValue(entitySource, propertyPathVars.toList ++ List(ref), false)

    override def productElement(n: Int): Any =
        n match {
            case 0 => entitySource
            case 1 => propertyPathVars
            case 2 => eager
        }
    override def productArity: Int = 3
    override def canEqual(that: Any): Boolean =
        that.getClass == classOf[StatementEntitySourcePropertyValue]
    override def equals(that: Any): Boolean =
        canEqual(that) && super.equals(that) &&
            that.asInstanceOf[StatementEntitySourcePropertyValue].propertyPathVars == propertyPathVars
}

case class ListValue[V](val fList: () => List[V], val f: V => StatementSelectValue) extends StatementValue {
    def list = fList()
    def statementSelectValueList = list.map(f)
    override def toString = list.toString
}

case class SimpleValue[V](val fAnyValue: () => V, val f: (Option[V]) => EntityValue[V]) extends StatementSelectValue {
    def anyValue = fAnyValue()
    def entityValue: EntityValue[V] = f(Option(anyValue))
    override def toString =
        if (anyValue == null)
            "null"
        else anyValue.toString
}