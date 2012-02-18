package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.WildcardRegexUtil.wildcardToRegex

class Operator() {
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
	implicit def toIsNull[V <% QueryValue](value: V) = IsNull(value)
	implicit def toIsNotNull[V <% QueryValue](value: V) = IsNotNull(value)
	implicit def toMatcher[V <% QueryValue](value: V) = Matcher(value)
}

class SimpleOperator() extends Operator
class CompositeOperator() extends Operator

case class Matcher(valueA: QueryValue) extends CompositeOperator {
	def like(valueB: String)(implicit f: Option[String] => EntityValue[String]) =
		CompositeOperatorCriteria(valueA, this, SimpleValue(wildcardToRegex(valueB), f))
	def regexp(valueB: String)(implicit f: Option[String] => EntityValue[String]) =
		CompositeOperatorCriteria(valueA, this, SimpleValue(valueB, f))
	override def toString = "matches"
}

case class IsNull(valueA: QueryValue) extends SimpleOperator {
	def isNull = SimpleOperatorCriteria(valueA, this)
	override def toString = "isNull"
}

case class IsNotNull(valueA: QueryValue) extends SimpleOperator {
	def isNotNull = SimpleOperatorCriteria(valueA, this)
	override def toString = "isNotNull"
}

case class IsEqualTo(valueA: QueryValue) extends CompositeOperator {
	def :==(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":=="
}

class ComparationOperator() extends CompositeOperator

case class IsGreaterThan(valueA: QueryValue) extends ComparationOperator {
	def :>(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":>"
}

case class IsLessThan(valueA: QueryValue) extends ComparationOperator {
	def :<(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":<"
}

case class IsGreaterOrEqualTo(valueA: QueryValue) extends ComparationOperator {
	def :>=(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":>="
}

case class IsLessOrEqualTo(valueA: QueryValue) extends ComparationOperator {
	def :<=(valueB: QueryValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":<="
}

abstract class BooleanOperator() extends CompositeOperator

case class And(valueA: QueryBooleanValue) extends BooleanOperator {
	def :&&(valueB: QueryBooleanValue) = BooleanOperatorCriteria(valueA, this, valueB)
	override def toString = "and"
}
case class Or(valueA: QueryBooleanValue) extends BooleanOperator {
	def :||(valueB: QueryBooleanValue) = BooleanOperatorCriteria(valueA, this, valueB)
	override def toString = "or"
}

abstract class Criteria() extends QueryBooleanValue

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

	private[activate] def selectList(list: List[QuerySelectValue[_]]) =
		Query[Product](From.from,
			this,
			Select(list: _*))

	def select[T1](tuple: => T1)(implicit tval1: (T1) => QuerySelectValue[T1]) =
		Query[T1](From.from,
			this,
			Select(tuple))

	def select[T1, T2](value1: => T1, value2: => T2)(implicit tval1: (T1) => QuerySelectValue[T1], tval2: (T2) => QuerySelectValue[T2]) =
		Query[Tuple2[T1, T2]](
			From.from,
			this,
			Select(tval1(value1),
				tval2(value2)))

	def select[T1, T2, T3](value1: => T1, value2: => T2, value3: => T3)(implicit tval1: (T1) => QuerySelectValue[T1], tval2: (T2) => QuerySelectValue[T2], tval3: (T3) => QuerySelectValue[T3]) =
		Query[Tuple3[T1, T2, T3]](
			From.from,
			this,
			Select(tval1(value1),
				tval2(value2),
				tval3(value3)))

	def select[T1, T2, T3, T4](value1: => T1, value2: => T2, value3: => T3, value4: => T4)(implicit tval1: (T1) => QuerySelectValue[T1], tval2: (T2) => QuerySelectValue[T2], tval3: (T3) => QuerySelectValue[T3], tval4: (T4) => QuerySelectValue[T4]) =
		Query[Tuple4[T1, T2, T3, T4]](
			From.from,
			this,
			Select(tval1(value1),
				tval2(value2),
				tval3(value3),
				tval4(value4)))

	override def toString = value.toString
}