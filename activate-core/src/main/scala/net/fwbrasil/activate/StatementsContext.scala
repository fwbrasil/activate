package net.fwbrasil.activate

import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.mass.MassModificationStatementNormalizer
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.statement.mass.MassModificationStatement

trait StatementsContext {
    this: ActivateContext =>

    private[activate] def currentTransactionStatements =
        transactionManager.getActiveTransaction.map(statementsForTransaction).getOrElse(ListBuffer())

    private[activate] def statementsForTransaction(transaction: Transaction) =
        transaction.attachments.collect {
            case s: MassModificationStatement =>
                s
        }

    private[activate] def executeMassModification(statement: MassModificationStatement) =
        for (normalized <- MassModificationStatementNormalizer.normalize[MassModificationStatement](statement)) {
            liveCache.executeMassModification(normalized)
            transactionManager.getRequiredActiveTransaction.attachments += normalized
        }

}