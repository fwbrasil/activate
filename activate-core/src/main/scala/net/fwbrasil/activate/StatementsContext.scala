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
        transactionManager.getActiveTransaction.map {
            transaction =>
                statementsForTransaction(transaction).getOrElse {
                    transactionStatements.doWithWriteLock {
                        transactionStatements.getOrElseUpdate(transaction, ListBuffer())
                    }
                }
        }.getOrElse(ListBuffer())

    private[activate] def statementsForTransaction(transaction: Transaction) =
        transactionStatements.doWithReadLock {
            transactionStatements.get(transaction)
        }

    private[activate] def executeMassModification(statement: MassModificationStatement) =
        for (normalized <- MassModificationStatementNormalizer.normalize[MassModificationStatement](statement)) {
            liveCache.executeMassModification(normalized)
            currentTransactionStatements += normalized
        }

    protected def clearStatements =
        transactionStatements.clear
}