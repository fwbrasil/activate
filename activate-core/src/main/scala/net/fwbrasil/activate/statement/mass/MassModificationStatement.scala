package net.fwbrasil.activate.statement.mass

import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.Statement

abstract class MassModificationStatement(from: From, where: Where)
	extends Statement(from, where)