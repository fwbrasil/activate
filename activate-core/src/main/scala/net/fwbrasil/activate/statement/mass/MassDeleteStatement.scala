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

trait MassDeleteContext extends StatementContext {
    import language.implicitConversions

    implicit def toDelete(where: Where) =
        MassDeleteStatement(From.from, where)
    import From._
    def produceDelete[S, E1 <: Entity: Manifest](f: (E1) => MassDeleteStatement): MassDeleteStatement =
        runAndClearFrom {
            f(mockEntity[E1])
        }
    def delete[S, E1 <: Entity: Manifest](f: (E1) => MassDeleteStatement): Unit =
        executeStatementWithCache[MassDeleteStatement, Unit](f, () => produceDelete(f), (delete: MassDeleteStatement) => delete.execute)

}

case class MassDeleteStatement(override val from: From, override val where: Where)
        extends MassModificationStatement(from, where) {

    override def toString = from + " => where" + where
}