package net.fwbrasil.activate.test

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.migration.ManualMigration
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.storage.memory.TransientMemoryStorage
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Future

trait ActivateTestStrategy {
    def runTest[R](f: => R)(implicit ctx: ActivateContext): R
    def runTestAsync[R](f: => R)(implicit ctx: ActivateContext): R
    def runTestAsyncChain[R](f: TransactionalExecutionContext => Future[R])(implicit ctx: ActivateContext): R

    protected def await[R](future: Future[R]) =
        Await.result(future, Duration.Inf)
}

object transactionRollbackStrategy extends ActivateTestStrategy {
    def runTest[R](f: => R)(implicit ctx: ActivateContext) = {
        import ctx._
        val transaction = new Transaction
        try
            transactional(transaction)(f)
        finally
            transaction.rollback
    }

    def runTestAsync[R](f: => R)(implicit ctx: ActivateContext): R =
        runTest(f)

    def runTestAsyncChain[R](f: TransactionalExecutionContext => Future[R])(implicit ctx: ActivateContext): R = {
        val ectx = new TransactionalExecutionContext()
        try await(f(ectx))
        finally ectx.transaction.rollback
    }
}

object cleanDatabaseStrategy extends ActivateTestStrategy {

    def runTest[R](f: => R)(implicit ctx: ActivateContext) = {
        prepareDatabase
        ctx.transactional(f)
    }

    def runTestAsync[R](f: => R)(implicit ctx: ActivateContext): R = {
        prepareDatabase
        await(ctx.asyncTransactional(f))
    }
    def runTestAsyncChain[R](f: TransactionalExecutionContext => Future[R])(implicit ctx: ActivateContext): R = {
        prepareDatabase
        await(ctx.asyncTransactionalChain(f(_)))
    }

    private def prepareDatabase(implicit ctx: ActivateContext) = {
        cleanDatabase
        ctx.reinitializeContext
    }

    private def cleanDatabase(implicit ctx: ActivateContext) = {
        import ctx._
        storage match {
            case storage: TransientMemoryStorage =>
                storage.directAccess.clear
            case other =>
                transactional {
                    delete {
                        (e: Entity) => where()
                    }
                }
        }
    }
}

object recreateDatabaseStrategy extends ActivateTestStrategy {

    def runTest[R](f: => R)(implicit ctx: ActivateContext) = {
        prepareDatabase
        ctx.transactional(f)
    }

    def runTestAsync[R](f: => R)(implicit ctx: ActivateContext): R = {
        prepareDatabase
        await(ctx.asyncTransactional(f))
    }
    def runTestAsyncChain[R](f: TransactionalExecutionContext => Future[R])(implicit ctx: ActivateContext): R = {
        prepareDatabase
        await(ctx.asyncTransactionalChain(f(_)))
    }

    def resetStorageVersion(implicit ctx: ActivateContext) =
        ctx.transactional {
            val storageVersion = Migration.storageVersionCache(ctx.name)
            storageVersion.lastScript = -1
            storageVersion.lastAction = -1
        }

    private def prepareDatabase(implicit ctx: ActivateContext) = {
        cleanDatabase
        resetStorageVersion
        ctx.reinitializeContext
        ctx.runMigration
    }

    private def cleanDatabase(implicit ctx: ActivateContext) =
        ctx.storage match {
            case storage: TransientMemoryStorage =>
                storage.directAccess.clear
            case other =>
                new ManualMigration {
                    def up = {
                        removeReferencesForAllEntities.ifExists
                        removeAllEntitiesTables.ifExists
                    }
                }.execute
        }
}

trait ActivateTest {

    protected val transactionRollback = transactionRollbackStrategy
    protected val cleanDatabase = cleanDatabaseStrategy
    protected val recreateDatabase = recreateDatabaseStrategy

    protected def defaultStrategy: ActivateTestStrategy = transactionRollbackStrategy

    protected def activateTest[R](strategy: ActivateTestStrategy)(test: => R)(implicit ctx: ActivateContext): R =
        strategy.runTest(test)

    protected def activateTest[R](test: => R)(implicit ctx: ActivateContext): R =
        activateTest(defaultStrategy)(test)

    protected def activateTestAsync[R](strategy: ActivateTestStrategy)(test: => R)(implicit ctx: ActivateContext): R =
        strategy.runTestAsync(test)

    protected def activateTestAsync[R](test: => R)(implicit ctx: ActivateContext): R =
        activateTestAsync(defaultStrategy)(test)

    protected def activateTestAsyncChain[R](strategy: ActivateTestStrategy)(test: TransactionalExecutionContext => Future[R])(implicit ctx: ActivateContext): R =
        strategy.runTestAsyncChain(test)

    protected def activateTestAsyncChain[R](test: TransactionalExecutionContext => Future[R])(implicit ctx: ActivateContext): R =
        activateTestAsyncChain(defaultStrategy)(test)
}