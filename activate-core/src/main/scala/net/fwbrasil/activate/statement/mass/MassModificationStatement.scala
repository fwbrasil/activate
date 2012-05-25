package net.fwbrasil.activate.statement.mass

import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.StatementNormalizer

abstract class MassModificationStatement(from: From, where: Where)
	extends Statement(from, where)

object MassModificationStatementNormalizer extends StatementNormalizer[MassModificationStatement] {

	def normalizeStatement(statement: MassModificationStatement): List[MassModificationStatement] =
		normalizeFrom(List(statement))
}