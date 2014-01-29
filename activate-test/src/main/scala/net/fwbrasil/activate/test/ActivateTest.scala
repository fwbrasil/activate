package net.fwbrasil.activate.test

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.migration.ManualMigration
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.storage.memory.TransientMemoryStorage

trait ActivateTestStrategy {
    def runTest[R](f: => R)(implicit context: ActivateContext): R
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
}

object cleanDatabaseStrategy extends ActivateTestStrategy {

    def runTest[R](f: => R)(implicit ctx: ActivateContext) = {
        import ctx._
        cleanDatabase
        ctx.reinitializeContext
        transactional(f)
    }

    private def cleanDatabase(implicit ctx: ActivateContext) {
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
        import ctx._
        cleanDatabase
        resetStorageVersion
        ctx.reinitializeContext
        ctx.runMigration
        transactional(f)
    }

    def resetStorageVersion(implicit ctx: ActivateContext) =
        ctx.transactional {
            val storageVersion = Migration.storageVersionCache(ctx.name)
            storageVersion.lastScript = -1
            storageVersion.lastAction = -1
        }

    def cleanDatabase(implicit ctx: ActivateContext) =
        new ManualMigration {
            def up = {
                removeReferencesForAllEntities.ifExists
                removeAllEntitiesTables.ifExists
            }
        }.execute
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

}