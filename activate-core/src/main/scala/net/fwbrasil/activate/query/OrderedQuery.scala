package net.fwbrasil.activate.query

import org.joda.time.base.AbstractInstant

trait OrderedQueryContext {

	implicit def abstractInstantOrdering[A <: AbstractInstant]: Ordering[A] = new Ordering[A] {
		def compare(x: A, y: A) = x.toDate.compareTo(y.toDate)
	}

	implicit def toOrderByCriteria[T](value: T)(implicit tval: (T) => QuerySelectValue[T], ordering: Ordering[T]) =
		OrderByCriteria[T](value, orderByAscendingDirection, ordering)

	implicit def toOrderByDirectionWrapper[T](value: T)(implicit tval: (T) => QuerySelectValue[T], ordering: Ordering[T]) =
		OrderByDirectionWrapper[T](value)

	implicit def toOrderByWrapper[S](query: Query[S]) =
		OrderByWrapper(query)
}

case class OrderByWrapper[S](query: Query[S]) {

	def orderBy(criterias: OrderByCriteria[_]*): Query[S] =
		if (!criterias.isEmpty)
			OrderedQuery[S](query.from, query.where, query.select, OrderBy(criterias: _*))
		else query
}

case class OrderedQuery[S](override val from: From, override val where: Where, override val select: Select, _orderBy: OrderBy)
		extends Query[S](from, where, select) {
	override def orderByClause = Some(_orderBy)
	override def toString = super.toString + _orderBy.toString
}

case class OrderBy(criterias: OrderByCriteria[_]*) {
	override def toString = " orderBy (" + criterias.mkString(", ") + ")"
}

abstract sealed class OrderByDirection
case object orderByAscendingDirection extends OrderByDirection {
	override def toString = "asc"
}
case object orderByDescendingDirection extends OrderByDirection {
	override def toString = "desc"
}

case class OrderByDirectionWrapper[T](value: QuerySelectValue[T])(implicit ordering: Ordering[T]) {
	def asc =
		OrderByCriteria[T](value, orderByAscendingDirection, ordering)
	def desc =
		OrderByCriteria[T](value, orderByDescendingDirection, ordering)
}

case class OrderByCriteria[T](value: QuerySelectValue[T], direction: OrderByDirection, _ordering: Ordering[T]) {
	def ordering =
		if (direction == orderByAscendingDirection)
			_ordering
		else
			_ordering.reverse
	override def toString = value.toString() + " " + direction.toString
}