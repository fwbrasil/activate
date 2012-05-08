package net.fwbrasil.activate.statement.mass

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._

@RunWith(classOf[JUnitRunner])
class MassDeleteSpecs extends ActivateTest {

	"Update framework" should {
		"delete entity" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
					}
					step {
						step.ctx.delete {
							(entity: ActivateTestEntity) => where(entity isNotNull)
						}.execute
					}
					step {
						all[ActivateTestEntity].map(_.intValue) must beEmpty
					}
				})
		}
		"delete specific entity" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val id =
						step {
							newEmptyActivateTestEntity.id
						}
					step {
						step.ctx.delete {
							(entity: ActivateTestEntity) => where(entity.id :== id)
						}.execute
					}
					step {
						all[ActivateTestEntity] must beEmpty
					}
				})
		}
		"update entities in memory" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						(1 to 10).foreach(i => newFullActivateTestEntity)
					}
					step {
						all[ActivateTestEntity].foreach(_.toString) // Just load entities
						step.ctx.delete {
							(entity: ActivateTestEntity) => where(entity isNotNull)
						}.execute
					}
					step {
						all[ActivateTestEntity].map(_.intValue) must beEmpty
					}
				})
		}
		"update specific entities in memory" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val ids = step {
						(1 to 10).map(i => newEmptyActivateTestEntity)
					}
					step {
						all[ActivateTestEntity].foreach(_.toString) // Just load entities
						step.ctx.delete {
							(entity: ActivateTestEntity) => where(entity.id :== ids.head)
						}.execute
					}
					step {
						all[ActivateTestEntity].size must beEqualTo(9)
					}
				})
		}
		"update entities partially in memory" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val ids =
						step {
							(1 to 10).map(i => newFullActivateTestEntity.id)
						}
					step {
						byId[ActivateTestEntity](ids.last).toString // Just load entity
						step.ctx.delete {
							(entity: ActivateTestEntity) => where(entity isNotNull)
						}.execute
					}
					step {
						all[ActivateTestEntity].map(_.intValue) must beEmpty
					}
				})
		}
		"update specific entities partially in memory" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val ids =
						step {
							(1 to 10).map(i => newEmptyActivateTestEntity.id)
						}
					step {
						byId[ActivateTestEntity](ids.last).toString // Just load entity
						step.ctx.delete {
							(entity: ActivateTestEntity) => where(entity.id :== ids.last)
						}.execute
					}
					step {
						all[ActivateTestEntity].size must beEqualTo(9)
					}
				})
		}

	}
}