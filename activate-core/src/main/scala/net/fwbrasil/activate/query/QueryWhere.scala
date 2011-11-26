package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity._

case class Operator() {
	QueryMocks.clearFakeVarCalled
}

abstract class QueryBooleanValue() extends QueryValue

case class SimpleQueryBooleanValue(value: Boolean)(implicit val tval: Boolean => EntityValue[Boolean]) extends QueryBooleanValue {
	def entityValue: EntityValue[Boolean] = value
	override def toString = value.toString
}

trait OperatorContext {
	implicit def toAnd(value: QueryBooleanValue) = And(value)
	implicit def toOr(value: QueryBooleanValue) = Or(value)
	implicit def toIsEqualTo[V <% QueryValue](value: V) = IsEqualTo(value)
	implicit def toIsGreaterThan[V <% QueryValue](value: V) = IsGreaterThan(value)
	implicit def toIsLessThan[V <% QueryValue](value: V) = IsLessThan(value)
	implicit def toIsGreaterOrEqualTo[V <% QueryValue](value: V) = IsGreaterOrEqualTo(value)
	implicit def toIsLessOrEqualTo[V <% QueryValue](value: V) = IsLessOrEqualTo(value)
	implicit def toIsNone[V <% QueryValue](value: V) = IsNone(value)
	implicit def toIsSomel[V <% QueryValue](value: V) = IsSome(value)
}

class SimpleOperator() extends Operator
class CompositeOperator() extends Operator

case class IsNone(valueA: QueryValue) extends SimpleOperator {
	def isNone = SimpleOperatorCriteria(valueA, this)
	override def toString = "isNull"
}

case class IsSome(valueA: QueryValue) extends SimpleOperator {
	def isSome = SimpleOperatorCriteria(valueA, this)
	override def toString = "isNotNull"
}

case class IsEqualTo(valueA : QueryValue) extends CompositeOperator {
	def :==(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":=="
}

class ComparationOperator() extends CompositeOperator

case class IsGreaterThan(valueA : QueryValue) extends ComparationOperator {
	def :>(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":>"
}

case class IsLessThan(valueA : QueryValue) extends ComparationOperator {
	def :<(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":<"
}

case class IsGreaterOrEqualTo(valueA : QueryValue) extends ComparationOperator {
	def :>=(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":>="
}

case class IsLessOrEqualTo(valueA : QueryValue) extends ComparationOperator {
	def :<=(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":<="
}

abstract case class BooleanOperator() extends CompositeOperator

case class And(valueA : QueryBooleanValue) extends BooleanOperator {
	def :&&(valueB: QueryBooleanValue) = BooleanOperatorCriteria(valueA, this, valueB)
	override def toString = "and"
}
case class Or(valueA : QueryBooleanValue) extends BooleanOperator {
	def :||(valueB: QueryBooleanValue) = BooleanOperatorCriteria(valueA, this, valueB)
	override def toString = "or"
}


abstract case class Criteria() extends QueryBooleanValue

case class SimpleOperatorCriteria(valueA: QueryValue, operator: SimpleOperator) extends Criteria {
	override def toString = "(" + valueA + " " + operator + ")"
}

case class CompositeOperatorCriteria(valueA: QueryValue, operator: CompositeOperator, valueB: QueryValue) extends Criteria {
	override def toString = "(" + valueA + " " + operator + " " + valueB + ")"
}
case class BooleanOperatorCriteria(valueA: QueryBooleanValue, operator: BooleanOperator, valueB: QueryBooleanValue) extends Criteria {
	override def toString = "(" + valueA + " " + operator + " " + valueB + ")"
}

case class Where(value: Criteria) {
	
	private[activate] def selectList
	          (list: List[QuerySelectValue[_]]) = 
		Query[Product](From.from, 
				  this, 
				  Select(list: _*))
	
	def select[V1, T1 <% QuerySelectValue[V1]]
	          (tuple: T1) = 
		Query[Tuple1[Option[V1]]](From.from, 
				  this, 
				  Select(tuple))
		
	def select[V1, T1 <% QuerySelectValue[V1], 
	           V2, T2 <% QuerySelectValue[V2]]
	          (tuple: Tuple2[T1, T2]) = 
		Query[Tuple2[Option[V1], Option[V2]]](
				From.from, 
				this, 
				Select(tuple._1, 
					   tuple._2))
	
	def select[V1, T1 <% QuerySelectValue[V1], 
	           V2, T2 <% QuerySelectValue[V2],
	           V3, T3 <% QuerySelectValue[V3]]
	          (tuple: Tuple3[T1, T2, T3]) = 
		Query[Tuple3[Option[V1], Option[V2], Option[V3]]](
				From.from, 
				this, 
				Select(tuple._1, 
					   tuple._2,
					   tuple._3))
	
	def select[V1, T1 <% QuerySelectValue[V1], 
	           V2, T2 <% QuerySelectValue[V2],
	           V3, T3 <% QuerySelectValue[V3],
	           V4, T4 <% QuerySelectValue[V4]]
	          (tuple: Tuple4[T1, T2, T3, T4]) = 
		Query[Tuple4[Option[V1], Option[V2], Option[V3], Option[V4]]](
				From.from, 
				this, 
				Select(tuple._1, 
					   tuple._2,
					   tuple._3,
					   tuple._4)) 
	override def toString = value.toString
}