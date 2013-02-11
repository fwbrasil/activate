package net.fwbrasil.activate.statement.mass

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.StatementNormalizer
import net.fwbrasil.activate.ActivateContext

abstract class MassModificationStatement(from: From, where: Where)
        extends Statement(from, where) {

    def execute: Unit = {
        val context =
            (for (src <- from.entitySources)
                yield ActivateContext.contextFor(src.entityClass)).toSet.onlyOne
        context.executeMassModification(this)
    }
}

object MassModificationStatementNormalizer extends StatementNormalizer[MassModificationStatement] {

    def normalizeStatement(statement: MassModificationStatement): List[MassModificationStatement] =
        normalizeFrom(List(statement))
}