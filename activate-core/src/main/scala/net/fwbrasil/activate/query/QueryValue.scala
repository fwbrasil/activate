package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query.QueryMocks.FakeVarToQuery

case class QueryValue

case class QuerySelectValue[V] extends QueryValue

trait QueryValueContext extends ValueContext {
	
	implicit def toQueryValue[V](ref: Var[V]): QuerySelectValue[V] = {
        val (entity, path) = propertyPath(ref)
        val sourceOption = From.entitySourceFor(entity)
        if (sourceOption != None)
            QueryEntitySourcePropertyValue[V](sourceOption.get, ref, path:_*)
        else
            SimpleValue[V](ref.get.get)(ref.tval.asInstanceOf[V => EntityValue[V]])
    }
	
	def propertyPath(ref: Var[_]) = {
		ref match {
			case ref: FakeVarToQuery[_] => {
				var propertyPath = List[String](ref.name)
				var entity = ref.outerEntity
				var originVar: FakeVarToQuery[_] = ref.originVar
				while(originVar != null) {
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
		
	
    implicit def toQueryValueEntity[E <: Entity](entity: E): QueryEntityValue[E] = {
        val sourceOption = From.entitySourceFor(entity)
        if (sourceOption != None)
            QueryEntitySourceValue(sourceOption.get)
        else
            QueryEntityInstanceValue(entity)
    }

    implicit def toQueryValue[V <% EntityValue[V]](value: V): QuerySelectValue[V] = {
        value match {
            case entity: Entity =>
                toQueryValueEntity[Entity](entity).asInstanceOf[QuerySelectValue[V]]
            case value =>
                SimpleValue[V](value)
        }
    }
    
    implicit def toSimpleQueryBooleanValue(value: Boolean) = 
    	SimpleQueryBooleanValue(value)

}

abstract case class QueryEntityValue[V] extends QuerySelectValue[V]

case class QueryEntityInstanceValue[E <: Entity](entity: E) extends QueryEntityValue[E] {
	def entityId = entity.id
    override def toString = entityId
}

case class QueryEntitySourceValue[V](entitySource: EntitySource) extends QueryEntityValue[V] {
    override def toString = entitySource.name
}

case class QueryEntitySourcePropertyValue[P](override val entitySource: EntitySource, ref: Var[P], propertyPath: String*) extends QueryEntitySourceValue[P](entitySource) {
    override def toString = entitySource.name + "." + propertyPath.mkString(".")
}

case class SimpleValue[V <% EntityValue[V]](anyValue: V) extends QuerySelectValue[V] {
	def entityValue: EntityValue[V] = anyValue
    override def toString = anyValue.toString
}