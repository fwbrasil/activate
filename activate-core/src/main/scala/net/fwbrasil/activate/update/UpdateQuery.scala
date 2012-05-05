//package net.fwbrasil.activate.update
//
//import net.fwbrasil.activate.statement.query.From
//import net.fwbrasil.activate.statement.query.OrderBy
//import net.fwbrasil.activate.statement.query.Query
//import net.fwbrasil.activate.statement.query.QuerySelectValue
//import net.fwbrasil.activate.statement.query.QueryValue
//import net.fwbrasil.activate.statement.query.Select
//import net.fwbrasil.activate.statement.query.Where
//import net.fwbrasil.activate.entity.Entity
//
//trait UpdateQueryContext {
//	implicit def toUpdateAssignee[T](value: T)(implicit tval: (T) => QuerySelectValue[T]) =
//		UpdateAssignee(tval(value))
//	import From._
//	def update[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): Query[S] =
//		null
//}
//
//case class UpdateAssignee(assignee: QuerySelectValue[_]) {
//	def :=[T](value: T)(implicit tval: (T) => QueryValue) =
//		UpdateAssignment(assignee, tval(value))
//}
//case class UpdateAssignment(assignee: QuerySelectValue[_], value: QueryValue)
//
//case class Update(from: From, where: Where, assignments: UpdateAssignment*) {
//
//}