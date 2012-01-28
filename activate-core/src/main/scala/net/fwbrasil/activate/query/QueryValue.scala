package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query.QueryMocks.FakeVarToQuery

class QueryValue()

class QuerySelectValue[V]() extends QueryValue

trait QueryValueContext extends ValueContext {

	private[activate] def toQueryValueRef[V](ref: Var[V]): QuerySelectValue[V] = {
		val (entity, path) = propertyPath(ref)
		val sourceOption = From.entitySourceFor(entity)
		if (sourceOption != None)
			new QueryEntitySourcePropertyValue[V](sourceOption.get, path: _*)
		else
			new SimpleValue[V](ref.get.get, ref.tval.asInstanceOf[V => EntityValue[V]])
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

	implicit def toQueryValueEntityValue[V](value: V)(implicit m: V => EntityValue[V]): QuerySelectValue[V] = {
		QueryMocks.lastFakeVarCalled match {
			case Some(ref) =>
				toQueryValueRef(ref.asInstanceOf[Var[V]])
			case other =>
				value match {
					case entity: Entity =>
						toQueryValueEntity[Entity](entity).asInstanceOf[QuerySelectValue[V]]
					case value =>
						new SimpleValue[V](value, m)

				}
		}
	}

}

abstract class QueryEntityValue[V]() extends QuerySelectValue[V]

case class QueryEntityInstanceValue[E <: Entity](val entity: E) extends QueryEntityValue[E] {
	def entityId = entity.id
	override def toString = entityId
}

case class QueryEntitySourceValue[V](val entitySource: EntitySource) extends QueryEntityValue[V] {
	override def toString = entitySource.name
}

case class QueryEntitySourcePropertyValue[P](override val entitySource: EntitySource, val propertyPathVars: Var[_]*) extends QueryEntitySourceValue[P](entitySource) {
	def lastVar = propertyPathVars.last
	def propertyPathNames =
		for (prop <- propertyPathVars)
			yield prop.name
	override def toString = entitySource.name + "." + propertyPathNames.mkString(".")
}

case class SimpleValue[V](val anyValue: V, val f: (V) => EntityValue[V]) extends QuerySelectValue[V] {
	require(anyValue != null)
	def entityValue: EntityValue[V] = f(anyValue)
	override def toString = anyValue.toString
}