package net.fwbrasil.activate

import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.util.ReferenceWeakKeyMap
import net.fwbrasil.activate.statement.mass.MassModificationStatementNormalizer
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.statement.mass.MassModificationStatement

trait StatementsContext {
    this: ActivateContext =>

    private[activate] def currentTransactionStatements = 
        transactionManager.getActiveTransaction.map(statementsForTransaction).getOrElse(ListBuffer())

    private[activate] def statementsForTransaction(transaction: Transaction) =
        transaction.attachments.asInstanceOf[ListBuffer[MassModificationStatement]]

    private[activate] def executeMassModification(statement: MassModificationStatement) =
        for (normalized <- MassModificationStatementNormalizer.normalize[MassModificationStatement](statement)) {
            liveCache.executeMassModification(normalized)
            currentTransactionStatements += normalized
        }

}