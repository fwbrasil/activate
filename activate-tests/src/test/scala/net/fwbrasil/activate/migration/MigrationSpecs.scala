//package net.fwbrasil.activate.migration
//
//import org.specs2.mutable._
//import org.junit.runner._
//import org.specs2.runner._
//import net.fwbrasil.activate.ActivateTest
//import net.fwbrasil.activate.ActivateTestContext
//import net.fwbrasil.activate.ActivateContext
//import net.fwbrasil.activate.util.RichList._
//
//@RunWith(classOf[JUnitRunner])
//class MigrationSpecs extends MigrationTest {
//
//	"Migration" should {
//		"create table" in {
//			migrationTest(
//				new SlaveTestMigration()(_) {
//					import context._
//					def up = {
//						createTableForEntity[EntityWithoutAttribute]
//					}
//				})((step: StepExecutor) => {
//					import step.ctx._
//					val entity =
//						step {
//							new EntityWithoutAttribute
//						}
//					step {
//						all[EntityWithoutAttribute].onlyOne must beEqualTo(entity)
//					}
//					step {
//						entity.delete
//					}
//				})
//		}
//	}
//}
