package net.fwbrasil.activate.statement.mass

import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.StatementContext

trait MassUpdateContext extends StatementContext {
	implicit def toUpdateAssignee[T](value: T)(implicit tval: (=> T) => StatementSelectValue[T]) =
		UpdateAssigneeDecorator(tval(value))
	implicit def toWhereDecorator(where: Where) =
		WhereUpdateDecorator(where)
	import From._
	def produceUpdate[S, E1 <: Entity: Manifest](f: (E1) => MassUpdateStatement): MassUpdateStatement =
		runAndClearFrom {
			f(mockEntity[E1])
		}
	def update[S, E1 <: Entity: Manifest](f: (E1) => MassUpdateStatement): Unit =
		executeStatementWithCache[MassUpdateStatement, Unit](f, () => produceUpdate(f), (update: MassUpdateStatement) => update.execute)

}

case class WhereUpdateDecorator(where: Where) {
	def set(assignments: UpdateAssignment*) =
		MassUpdateStatement(From.from, where, assignments: _*)
}

case class UpdateAssigneeDecorator(assignee: StatementSelectValue[_]) {
	def :=[T](value: => T)(implicit tval: (=> T) => StatementValue) =
		UpdateAssignment(assignee, tval(value))
}
case class UpdateAssignment(assignee: StatementSelectValue[_], value: StatementValue) {
	override def toString = assignee.toString + " := " + value.toString
}

case class MassUpdateStatement(override val from: From, override val where: Where, assignments: UpdateAssignment*)
		extends MassModificationStatement(from, where) {

	def execute: Unit = {
		val context =
			(for (src <- from.entitySources)
				yield ActivateContext.contextFor(src.entityClass)).toSet.onlyOne
		context.executeMassModification(this)
	}

	override def toString = from + " => where" + where + " set " + assignments.mkString(", ") + ""
}