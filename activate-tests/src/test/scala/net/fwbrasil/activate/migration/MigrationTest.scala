package net.fwbrasil.activate.migration

import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.runningFlag

class MigrationTest extends ActivateTest {

    abstract class TestMigration(override implicit val context: ActivateTestContext) extends ManualMigration {

        def validateSchemaError(f: => Unit) =
            if (!context.storage.isSchemaless)
                context.transactional {
                    f
                } must throwA[Exception]

        def validateUp: Unit = {}
        def validateDown: Unit = {}
    }

    def migrationTest(registers: ((ActivateTestContext) => TestMigration)*) =
        runningFlag.synchronized {
            for (ctx <- contexts) {
                import ctx._
                ctx.start
                def clear = {
                    Migration.storageVersion(ctx) // Crate StorageVersion if not exists
                    ctx.transactional {
                        ctx.delete {
                            (s: StorageVersion) => where()
                        }
                    }
                    if (ctx.storage.isInstanceOf[PooledJdbcRelationalStorage])
                        ctx.storage.asInstanceOf[PooledJdbcRelationalStorage].reinitialize
                    ActivateContext.clearCaches()
                    Migration.migrationsCache.clear
                    Migration.storageVersionCache.clear
                }
                clear

                val bootstrap = new TestMigration()(ctx) {
                    def up = {
                        removeReferencesForAllEntities
                            .ifExists
                        removeAllEntitiesTables
                            .ifExists
                            .cascade
                    }

                }
                Migration.execute(ctx, bootstrap)
                val migrations = registers.map(_(ctx))
                try {
                    for (migration <- migrations) {
                        ctx.reinitializeContext
                        Migration.execute(ctx, migration)
                        migration.validateUp
                    }
                    for (migration <- migrations.reverse) {
                        ctx.reinitializeContext
                        Migration.revert(ctx, migration)
                        migration.validateDown
                    }
                } finally {
                    clear
                    stop
                }
            }
            true must beTrue
        }
}