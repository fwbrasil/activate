package net.fwbrasil.activate.query

import org.joda.time.base.AbstractInstant

trait OrderedQueryContext {
	implicit def abstractInstantOrdering[A <: AbstractInstant]: Ordering[A] = new Ordering[A] {
		def compare(x: A, y: A) = x.toDate.compareTo(y.toDate)
	}

	implicit def Tuple1[T1](implicit ord: Ordering[T1]): Ordering[Tuple1[T1]] =
		new Ordering[Tuple1[T1]] {
			def compare(x: Tuple1[T1], y: Tuple1[T1]): Int = {
				ord.compare(x._1, y._1)
			}
		}

	implicit def toOrderByCriteria[T <% QuerySelectValue[T]](value: T): OrderByCriteria[T] =
		OrderByCriteria[T](orderByAscendingDirection, value)

	implicit def toOrderByDirectionWrapper[T <% QuerySelectValue[T]](value: T) =
		OrderByDirectionWrapper[T](value)

	implicit def toOrderByWrapper[S](query: Query[S]) =
		OrderByWrapper(query)
}

case class OrderByWrapper[S](query: Query[S]) {

	def orderBy[T1](value: OrderByCriteria[T1])(implicit ordering: Ordering[Tuple1[T1]]) =
		orderedQuery(ordering, value)

	def orderBy[T1, T2](
		value1: OrderByCriteria[T1],
		value2: OrderByCriteria[T2])(implicit ordering: Ordering[Tuple2[T1, T2]]) =
		orderedQuery(
			ordering,
			value1,
			value2)

	def orderBy[T1, T2, T3](
		value1: OrderByCriteria[T1],
		value2: OrderByCriteria[T2],
		value3: OrderByCriteria[T3])(implicit ordering: Ordering[Tuple3[T1, T2, T3]]) =
		orderedQuery(
			ordering,
			value1,
			value2,
			value3)

	def orderBy[T1, T2, T3, T4](
		value1: OrderByCriteria[T1],
		value2: OrderByCriteria[T2],
		value3: OrderByCriteria[T3],
		value4: OrderByCriteria[T3])(implicit ordering: Ordering[Tuple4[T1, T2, T3, T4]]) =
		orderedQuery(
			ordering,
			value1,
			value2,
			value3,
			value4)

	private[this] def orderedQuery(ordering: Ordering[_], values: OrderByCriteria[_]*): Query[S] =
		OrderedQuery[S](query.from, query.where, query.select, OrderBy(ordering, values: _*))
}

case class OrderedQuery[S](override val from: From, override val where: Where, override val select: Select, _orderBy: OrderBy)
		extends Query[S](from, where, select) {
	override def orderByClause = Some(_orderBy)
	override def toString = super.toString + _orderBy.toString
}

case class OrderBy(ordering: Ordering[_], criterias: OrderByCriteria[_]*) {
	override def toString = " orderBy (" + criterias.mkString(", ") + ")"
}

abstract sealed class OrderByDirection
case object orderByAscendingDirection extends OrderByDirection {
	override def toString = "asc"
}
case object orderByDescendingDirection extends OrderByDirection {
	override def toString = "desc"
}

case class OrderByDirectionWrapper[T](value: QuerySelectValue[T]) {
	def asc =
		OrderByCriteria[T](orderByAscendingDirection, value)
	def desc =
		OrderByCriteria[T](orderByDescendingDirection, value)
}

case class OrderByCriteria[T](direction: OrderByDirection, value: QuerySelectValue[T]) {
	def this(value: QuerySelectValue[T]) =
		this(orderByAscendingDirection, value)
	override def toString = value.toString() + " " + direction.toString
}