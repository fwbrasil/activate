//package net.fwbrasil.activate.migration
//
//import net.fwbrasil.activate.ActivateTestContext
//import net.fwbrasil.activate.ActivateTest
//
//class MigrationTest extends ActivateTest {
//
//	abstract class SlaveTestMigration(override implicit val context: ActivateTestContext) extends Migration {
//
//		Migration.migrationsCache.put(context, Migration.migrationsCache.getOrElse(context, List()) ++ List(this))
//
//		def timestamp = Migration.migrationsCache(context).indexOf(this)
//		val name = "Slave test Migration"
//		val developers = List("fwbrasil")
//
//	}
//
//	def migrationTest(registers: ((ActivateTestContext) => SlaveTestMigration)*)(f: (StepExecutor) => Unit) = {
//		for (ctx <- contexts) {
//			import ctx._
//			new SlaveTestMigration()(ctx) {
//				def up = {
//					removeAllEntitiesTables
//						.ifExists
//						.cascade
//				}
//			}
//			registers.foreach(_(ctx))
//			ctx.start
//			try {
//				for (executor <- executors(ctx)) {
//					ctx.runMigration
//					f(executor)
//					executor.finalizeExecution
//				}
//			} finally
//				stop
//		}
//		true must beTrue
//	}
//}