package net.fwbrasil.activate.migration

import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.runningFlag
import net.fwbrasil.activate.entity.Entity

class MigrationTest extends ActivateTest {

	@MigrationBootstrap
	abstract class TestMigration(override implicit val context: ActivateTestContext) extends Migration {

		Migration.migrationsCache.put(context, Migration.migrationsCache.getOrElse(context, List()) ++ List(this))

		def timestamp = Migration.migrationsCache(context).indexOf(this)
		val name = "Test Migration"
		val developers = List("tester")

		def validateUp: Unit = {}
		def validateDown: Unit = {}
	}

	def migrationTest(registers: ((ActivateTestContext) => TestMigration)*) =
		runningFlag.synchronized {
			for (ctx <- contexts) {
				import ctx._
				ctx.start
				ctx.transactional {
					ctx.delete {
						(s: StorageVersion) => where(s isNotNull)
					}
				}
				ActivateContext.clearContextCache
				Migration.migrationsCache.clear
				Migration.storageVersionCache.clear
				new TestMigration()(ctx) {
					def up = {
						removeReferencesForAllEntities
							.ifExists
						removeAllEntitiesTables
							.ifExists
							.cascade
					}

				}
				val migrations = registers.map(_(ctx))
				try {
					for (migration <- migrations) {
						Migration.updateTo(ctx, migration.timestamp)
						migration.validateUp
					}
					for (migration <- migrations.reverse) {
						Migration.revertTo(ctx, migration.timestamp - 1)
						migration.validateDown
					}
				} catch {
					case e =>
						e.printStackTrace
						throw e
				} finally {
					ctx.transactional {
						ctx.delete {
							(s: StorageVersion) => where(s isNotNull)
						}
					}
					ActivateContext.clearContextCache
					Migration.migrationsCache.clear
					Migration.storageVersionCache.clear
					stop
				}
			}
			true must beTrue
		}
}