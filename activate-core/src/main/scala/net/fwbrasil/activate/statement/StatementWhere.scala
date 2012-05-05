package net.fwbrasil.activate.statement

import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.WildcardRegexUtil.wildcardToRegex
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.query.Select

class Operator() {
	StatementMocks.clearFakeVarCalled
}

abstract class StatementBooleanValue() extends StatementValue

case class SimpleStatementBooleanValue(value: Boolean)(implicit val tval: Boolean => EntityValue[Boolean]) extends StatementBooleanValue {
	def entityValue: EntityValue[Boolean] = value
	override def toString = value.toString
}

trait OperatorContext {
	implicit def toAnd(value: StatementBooleanValue) = And(value)
	implicit def toOr(value: StatementBooleanValue) = Or(value)
	implicit def toIsEqualTo[V <% StatementValue](value: V) = IsEqualTo(value)
	implicit def toIsGreaterThan[V <% StatementValue](value: V) = IsGreaterThan(value)
	implicit def toIsLessThan[V <% StatementValue](value: V) = IsLessThan(value)
	implicit def toIsGreaterOrEqualTo[V <% StatementValue](value: V) = IsGreaterOrEqualTo(value)
	implicit def toIsLessOrEqualTo[V <% StatementValue](value: V) = IsLessOrEqualTo(value)
	implicit def toIsNull[V <% StatementValue](value: V) = IsNull(value)
	implicit def toIsNotNull[V <% StatementValue](value: V) = IsNotNull(value)
	implicit def toMatcher[V <% StatementValue](value: V) = Matcher(value)
}

class SimpleOperator() extends Operator
class CompositeOperator() extends Operator

case class Matcher(valueA: StatementValue) extends CompositeOperator {
	def like(valueB: String)(implicit f: Option[String] => EntityValue[String]) =
		CompositeOperatorCriteria(valueA, this, SimpleValue(wildcardToRegex(valueB), f))
	def regexp(valueB: String)(implicit f: Option[String] => EntityValue[String]) =
		CompositeOperatorCriteria(valueA, this, SimpleValue(valueB, f))
	override def toString = "matches"
}

case class IsNull(valueA: StatementValue) extends SimpleOperator {
	def isNull = SimpleOperatorCriteria(valueA, this)
	override def toString = "isNull"
}

case class IsNotNull(valueA: StatementValue) extends SimpleOperator {
	def isNotNull = SimpleOperatorCriteria(valueA, this)
	override def toString = "isNotNull"
}

case class IsEqualTo(valueA: StatementValue) extends CompositeOperator {
	def :==(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":=="
}

class ComparationOperator() extends CompositeOperator

case class IsGreaterThan(valueA: StatementValue) extends ComparationOperator {
	def :>(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":>"
}

case class IsLessThan(valueA: StatementValue) extends ComparationOperator {
	def :<(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":<"
}

case class IsGreaterOrEqualTo(valueA: StatementValue) extends ComparationOperator {
	def :>=(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":>="
}

case class IsLessOrEqualTo(valueA: StatementValue) extends ComparationOperator {
	def :<=(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
	override def toString = ":<="
}

abstract class BooleanOperator() extends CompositeOperator

case class And(valueA: StatementBooleanValue) extends BooleanOperator {
	def :&&(valueB: StatementBooleanValue) = BooleanOperatorCriteria(valueA, this, valueB)
	override def toString = "and"
}
case class Or(valueA: StatementBooleanValue) extends BooleanOperator {
	def :||(valueB: StatementBooleanValue) = BooleanOperatorCriteria(valueA, this, valueB)
	override def toString = "or"
}

abstract class Criteria() extends StatementBooleanValue

case class SimpleOperatorCriteria(valueA: StatementValue, operator: SimpleOperator) extends Criteria {
	override def toString = "(" + valueA + " " + operator + ")"
}

case class CompositeOperatorCriteria(valueA: StatementValue, operator: CompositeOperator, valueB: StatementValue) extends Criteria {
	override def toString = "(" + valueA + " " + operator + " " + valueB + ")"
}
case class BooleanOperatorCriteria(valueA: StatementBooleanValue, operator: BooleanOperator, valueB: StatementBooleanValue) extends Criteria {
	override def toString = "(" + valueA + " " + operator + " " + valueB + ")"
}

case class Where(value: Criteria) {
	private[activate] def selectList(list: List[StatementSelectValue[_]]) =
		Query[Product](From.from,
			this,
			Select(list: _*))

	def select[T1](tuple: => T1)(implicit tval1: (T1) => StatementSelectValue[T1]) =
		Query[T1](From.from,
			this,
			Select(tuple))

	def select[T1, T2](value1: => T1, value2: => T2)(implicit tval1: (T1) => StatementSelectValue[T1], tval2: (T2) => StatementSelectValue[T2]) =
		Query[Tuple2[T1, T2]](
			From.from,
			this,
			Select(tval1(value1),
				tval2(value2)))

	def select[T1, T2, T3](value1: => T1, value2: => T2, value3: => T3)(implicit tval1: (T1) => StatementSelectValue[T1], tval2: (T2) => StatementSelectValue[T2], tval3: (T3) => StatementSelectValue[T3]) =
		Query[Tuple3[T1, T2, T3]](
			From.from,
			this,
			Select(tval1(value1),
				tval2(value2),
				tval3(value3)))

	def select[T1, T2, T3, T4](value1: => T1, value2: => T2, value3: => T3, value4: => T4)(implicit tval1: (T1) => StatementSelectValue[T1], tval2: (T2) => StatementSelectValue[T2], tval3: (T3) => StatementSelectValue[T3], tval4: (T4) => StatementSelectValue[T4]) =
		Query[Tuple4[T1, T2, T3, T4]](
			From.from,
			this,
			Select(tval1(value1),
				tval2(value2),
				tval3(value3),
				tval4(value4)))
	override def toString = value.toString
}