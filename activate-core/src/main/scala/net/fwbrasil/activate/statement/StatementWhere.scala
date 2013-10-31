package net.fwbrasil.activate.statement

import language.existentials
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.WildcardRegexUtil.wildcardToRegex
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.query.Select
import scala.annotation.implicitNotFound
import net.fwbrasil.activate.entity.StringEntityValue

class Operator() {
    StatementMocks.clearFakeVarCalled
}

abstract class StatementBooleanValue() extends StatementValue

case class SimpleStatementBooleanValue(value: Boolean)(implicit val tval: Boolean => EntityValue[Boolean]) extends StatementBooleanValue {
    def entityValue: EntityValue[Boolean] = value
    override def toString = value.toString
}

trait OperatorContext {
    import language.implicitConversions

    implicit def toAnd(value: StatementBooleanValue) = And(value)
    implicit def toOr(value: StatementBooleanValue) = Or(value)
    implicit def toIsEqualTo[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsEqualTo(value)
    implicit def toIsNotEqualTo[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsNotEqualTo(value)
    implicit def toIsGreaterThan[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsGreaterThan(value)
    implicit def toIsLessThan[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsLessThan(value)
    implicit def toIsGreaterOrEqualTo[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsGreaterOrEqualTo(value)
    implicit def toIsLessOrEqualTo[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsLessOrEqualTo(value)
    implicit def toIsNull[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsNull(value)
    implicit def toIsNotNull[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = IsNotNull(value)
    implicit def toMatcher[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = Matcher(value)
    implicit def toIn[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = In(value)
    implicit def toNotIn[V](value: V)(implicit tval1: (=> V) => StatementSelectValue) = NotIn(value)
    
    def toUpperCase(value: String)(implicit tval1: (=> String) => StatementSelectValue) = ToUpperCase(value)
    def toLowerCase(value: String)(implicit tval1: (=> String) => StatementSelectValue) = ToLowerCase(value)
}

class SimpleOperator() extends Operator
class CompositeOperator() extends Operator

case class Matcher(valueA: StatementSelectValue) extends CompositeOperator {
    def like(valueB: => String)(implicit f: Option[String] => EntityValue[String]) =
        CompositeOperatorCriteria(valueA, this, SimpleValue(() => wildcardToRegex(valueB), f))
    def regexp(valueB: => String)(implicit f: Option[String] => EntityValue[String]) =
        CompositeOperatorCriteria(valueA, this, SimpleValue(() => valueB, f))
    override def toString = "matches"
}

case class In(valueA: StatementSelectValue) extends CompositeOperator {
    def in[V](valueB: => Iterable[V])(implicit tval: Option[V] => EntityValue[V], ctx: StatementValueContext) =
        CompositeOperatorCriteria(valueA, this, ListValue(() => valueB.toList, (v: V) => ctx.toStatementValueEntityValue(v)))
    override def toString = "in"
}

case class NotIn(valueA: StatementSelectValue) extends CompositeOperator {
    def notIn[V](valueB: => Iterable[V])(implicit tval: Option[V] => EntityValue[V], ctx: StatementValueContext) =
        CompositeOperatorCriteria(valueA, this, ListValue(() => valueB.toList, (v: V) => ctx.toStatementValueEntityValue(v)))
    override def toString = "in"
}

case class IsNull(valueA: StatementSelectValue) extends SimpleOperator {
    def isNull = SimpleOperatorCriteria(valueA, this)
    override def toString = "isNull"
}

case class ToUpperCase(value: StatementSelectValue) extends FunctionApply(value) {
    override def toString = s"toUpperCase($value)"
    def entityValue = value.entityValue.asInstanceOf[StringEntityValue].value.map(_.toUpperCase)
}

case class ToLowerCase(value: StatementSelectValue) extends FunctionApply(value) {
    override def toString = s"toLowerCase($value)"
    def entityValue = value.entityValue.asInstanceOf[StringEntityValue].value.map(_.toUpperCase)
}

case class IsNotNull(valueA: StatementSelectValue) extends SimpleOperator {
    def isNotNull = SimpleOperatorCriteria(valueA, this)
    override def toString = "isNotNull"
}

case class IsEqualTo(valueA: StatementSelectValue) extends CompositeOperator {
    def :==(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
    override def toString = ":=="
}

case class IsNotEqualTo(valueA: StatementSelectValue) extends CompositeOperator {
    def :!=(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
    override def toString = ":!="
}

class ComparationOperator() extends CompositeOperator

case class IsGreaterThan(valueA: StatementSelectValue) extends ComparationOperator {
    def :>(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
    override def toString = ":>"
}

case class IsLessThan(valueA: StatementSelectValue) extends ComparationOperator {
    def :<(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
    override def toString = ":<"
}

case class IsGreaterOrEqualTo(valueA: StatementSelectValue) extends ComparationOperator {
    def :>=(valueB: StatementValue) = CompositeOperatorCriteria(valueA, this, valueB)
    override def toString = ":>="
}

case class IsLessOrEqualTo(valueA: StatementSelectValue) extends ComparationOperator {
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

abstract class FunctionApply[V](value: StatementSelectValue) extends StatementSelectValue {
}

case class CompositeOperatorCriteria(valueA: StatementValue, operator: CompositeOperator, valueB: StatementValue) extends Criteria {
    override def toString = "(" + valueA + " " + operator + " " + valueB + ")"
}
case class BooleanOperatorCriteria(valueA: StatementBooleanValue, operator: BooleanOperator, valueB: StatementBooleanValue) extends Criteria {
    override def toString = "(" + valueA + " " + operator + " " + valueB + ")"
}

case class Where(valueOption: Option[Criteria]) {

    private[activate] def selectList(list: List[StatementSelectValue]) =
        new Query[Product](From.from,
            this,
            Select(list: _*))

    @implicitNotFound("Can't find a EntityValue implicit converter. Maybe the select type is not supported.")
    def select[T1](tuple: => T1)(implicit tval1: (=> T1) => StatementSelectValue) =
        new Query[T1](From.from,
            this,
            Select(tuple))

    @implicitNotFound("Can't find a EntityValue implicit converter. Maybe the select type is not supported.")
    def select[T1, T2](value1: => T1, value2: => T2)(implicit tval1: (=> T1) => StatementSelectValue, tval2: (=> T2) => StatementSelectValue) =
        new Query[Tuple2[T1, T2]](
            From.from,
            this,
            Select(tval1(value1),
                tval2(value2)))

    @implicitNotFound("Can't find a EntityValue implicit converter. Maybe the select type is not supported.")
    def select[T1, T2, T3](value1: => T1, value2: => T2, value3: => T3)(implicit tval1: (=> T1) => StatementSelectValue, tval2: (=> T2) => StatementSelectValue, tval3: (=> T3) => StatementSelectValue) =
        new Query[Tuple3[T1, T2, T3]](
            From.from,
            this,
            Select(tval1(value1),
                tval2(value2),
                tval3(value3)))
    @implicitNotFound("Can't find a EntityValue implicit converter. Maybe the select type is not supported.")
    def select[T1, T2, T3, T4](value1: => T1, value2: => T2, value3: => T3, value4: => T4)(implicit tval1: (=> T1) => StatementSelectValue, tval2: (=> T2) => StatementSelectValue, tval3: (=> T3) => StatementSelectValue, tval4: (=> T4) => StatementSelectValue) =
        new Query[Tuple4[T1, T2, T3, T4]](
            From.from,
            this,
            Select(tval1(value1),
                tval2(value2),
                tval3(value3),
                tval4(value4)))

    override def toString = valueOption.map(_.toString).getOrElse("()")
}