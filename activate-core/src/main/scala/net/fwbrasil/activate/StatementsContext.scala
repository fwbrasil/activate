package net.fwbrasil.activate

import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.util.ReferenceWeakKeyMap
import net.fwbrasil.activate.statement.mass.MassModificationStatementNormalizer
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.SynchronizedMap
import net.fwbrasil.radon.util.Lockable

trait StatementsContext {
    this: ActivateContext =>

    private val transactionStatements =
        new ReferenceWeakKeyMap[Transaction, ListBuffer[MassModificationStatement]]() with Lockable

    private[activate] def currentTransactionStatements =
        transactionManager.getActiveTransaction.map(statementsForTransaction).getOrElse(ListBuffer())

    private[activate] def statementsForTransaction(transaction: Transaction) =
        transactionStatements.doWithReadLock {
            transactionStatements.get(transaction)
        }.getOrElse {
            transactionStatements.doWithWriteLock {
                transactionStatements.getOrElseUpdate(transaction, ListBuffer())
            }
        }
    private[activate] def executeMassModification(statement: MassModificationStatement) =
        for (normalized <- MassModificationStatementNormalizer.normalize[MassModificationStatement](statement)) {
            liveCache.executeMassModification(normalized)
            currentTransactionStatements += normalized
        }

    protected def clearStatements =
        transactionStatements.clear
}