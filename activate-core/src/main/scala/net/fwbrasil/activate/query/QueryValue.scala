package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query.QueryMocks.FakeVarToQuery

class QueryValue()

class QuerySelectValue[V]() extends QueryValue

trait QueryValueContext extends ValueContext {

	def toQueryValueRef[V](ref: Var[V]): QuerySelectValue[V] = {
		val (entity, path) = propertyPath(ref)
		val sourceOption = From.entitySourceFor(entity)
		if (sourceOption != None)
			new QueryEntitySourcePropertyValue[V](sourceOption.get, ref, path: _*)
		else
			new SimpleValue[V](ref.get.get)(ref.tval.asInstanceOf[V => EntityValue[V]])
	}

	def propertyPath(ref: Var[_]) = {
		ref match {
			case ref: FakeVarToQuery[_] => {
				var propertyPath = List[String](ref.name)
				var entity = ref.outerEntity
				var originVar: FakeVarToQuery[_] = ref.originVar
				while (originVar != null) {
					propertyPath ::= originVar.name
					entity = originVar.outerEntity
					originVar = originVar.originVar
				}
				(entity, propertyPath)
			}
			case ref: Var[_] =>
				(null, Nil)
		}
	}

	def toQueryValueEntity[E <: Entity](entity: E): QueryEntityValue[E] = {
		val sourceOption = From.entitySourceFor(entity)
		if (sourceOption != None)
			new QueryEntitySourceValue(sourceOption.get)
		else
			new QueryEntityInstanceValue(entity)
	}

	implicit def toQueryValueEntityValue[V](value: V)(implicit m: V => EntityValue[V]): QuerySelectValue[V] = {
		QueryMocks.lastFakeVarCalled match {
			case Some(ref: Var[_]) =>
				toQueryValueRef(ref.asInstanceOf[Var[V]])
			case other =>
				value match {
					case entity: Entity =>
						toQueryValueEntity[Entity](entity).asInstanceOf[QuerySelectValue[V]]
					case value =>
						new SimpleValue[V](value)

				}
		}
	}

}

abstract class QueryEntityValue[V]() extends QuerySelectValue[V]

class QueryEntityInstanceValue[E <: Entity](val entity: E) extends QueryEntityValue[E] {
	def entityId = entity.id
	override def toString = entityId
}

class QueryEntitySourceValue[V](val entitySource: EntitySource) extends QueryEntityValue[V] {
	override def toString = entitySource.name
}

class QueryEntitySourcePropertyValue[P](override val entitySource: EntitySource, val ref: Var[P], val propertyPath: String*) extends QueryEntitySourceValue[P](entitySource) {
	override def toString = entitySource.name + "." + propertyPath.mkString(".")
}

class SimpleValue[V <% EntityValue[V]](val anyValue: V) extends QuerySelectValue[V] {
	require(anyValue != null)
	def entityValue: EntityValue[V] = anyValue
	override def toString = anyValue.toString
}