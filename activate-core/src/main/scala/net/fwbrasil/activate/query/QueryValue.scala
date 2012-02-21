package net.fwbrasil.activate.query

import net.fwbrasil.activate.query.QueryMocks.FakeVarToQuery
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.ValueContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.entity.EntityInstanceEntityValue

class QueryValue()

abstract class QuerySelectValue[V]() extends QueryValue {
	def entityValue: EntityValue[_]
}

trait QueryValueContext extends ValueContext {

	private[activate] def toQueryValueRef[V](ref: Var[V]): QuerySelectValue[V] = {
		val (entity, path) = propertyPath(ref)
		val sourceOption = From.entitySourceFor(entity)
		if (sourceOption != None)
			new QueryEntitySourcePropertyValue[V](sourceOption.get, path: _*)
		else new SimpleValue[V](ref.get.get, ref.tval)

	}

	private[activate] def propertyPath(ref: Var[_]) = {
		ref match {
			case ref: FakeVarToQuery[_] => {
				var propertyPath = List[Var[_]](ref)
				var entity = ref.outerEntity
				var originVar: FakeVarToQuery[_] = ref.originVar
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

	private[activate] def toQueryValueEntity[E <: Entity](entity: E): QueryEntityValue[E] = {
		val sourceOption = From.entitySourceFor(entity)
		if (sourceOption != None)
			new QueryEntitySourceValue(sourceOption.get)
		else
			new QueryEntityInstanceValue(entity)
	}

	implicit def toQueryValueEntityValue[V](value: V)(implicit m: Option[V] => EntityValue[V]): QuerySelectValue[V] =
		toQueryValueEntityValueOption(Option(value))

	implicit def toQueryValueEntityValueOption[V](value: Option[V])(implicit m: Option[V] => EntityValue[V]): QuerySelectValue[V] = {
		QueryMocks.lastFakeVarCalled match {
			case Some(ref: V) =>
				toQueryValueRef(ref)
			case other =>
				value.getOrElse(null.asInstanceOf[V]) match {
					case entity: Entity =>
						toQueryValueEntity(entity).asInstanceOf[QuerySelectValue[V]]
					case value =>
						new SimpleValue[V](value, m)

				}
		}
	}

}

abstract class QueryEntityValue[V]() extends QuerySelectValue[V]

case class QueryEntityInstanceValue[E <: Entity](val entity: E) extends QueryEntityValue[E] {
	def entityId = entity.id
	override def entityValue = EntityInstanceEntityValue[E](Option(entity))(manifestClass[E](entity.getClass))
	override def toString = entityId
	override def hashCode = System.identityHashCode(this)
}

case class QueryEntitySourceValue[V](val entitySource: EntitySource) extends QueryEntityValue[V] {
	override def entityValue: EntityValue[_] = EntityInstanceEntityValue(None)(manifestClass(entitySource.entityClass))
	override def toString = entitySource.name
}

case class QueryEntitySourcePropertyValue[P](override val entitySource: EntitySource, val propertyPathVars: Var[_]*) extends QueryEntitySourceValue[P](entitySource) {
	def lastVar = propertyPathVars.last
	def propertyPathNames =
		for (prop <- propertyPathVars)
			yield prop.name
	override def entityValue: EntityValue[_] = lastVar.asInstanceOf[QueryMocks.FakeVarToQuery[_]].entityValueMock.asInstanceOf[EntityValue[_]]
	override def toString = entitySource.name + "." + propertyPathNames.mkString(".")
}

case class SimpleValue[V](val anyValue: V, val f: (Option[V]) => EntityValue[V]) extends QuerySelectValue[V] {
	require(anyValue != null)
	def entityValue: EntityValue[V] = f(Option(anyValue))
	override def toString = anyValue.toString
}