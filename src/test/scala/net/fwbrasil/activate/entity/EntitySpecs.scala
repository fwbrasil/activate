package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest

@RunWith(classOf[JUnitRunner])
class EntitySpecs extends ActivateTest {

	"Entity" should {

		"be in live cache" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for (entity <- all[ActivateTestEntity])
							entity.isInLiveCache must beTrue
					}
				}
			)
		}

		"return vars fields" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for (entity <- all[ActivateTestEntity])
							entity.vars.toSet.size must beEqualTo(12)
					}
				}
			)
		}

		"return id field" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for (entity <- all[ActivateTestEntity])
							entity.idField must not beNull
					}
				}
			)
		}

		"return var named" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for (entity <- all[ActivateTestEntity]) {
							Option(entity.intValue) must beEqualTo(entity.varNamed("intValue"))
							Option(entity.booleanValue) must beEqualTo(entity.varNamed("booleanValue"))
							Option(entity.charValue) must beEqualTo(entity.varNamed("charValue"))
							Option(entity.stringValue) must beEqualTo(entity.varNamed("stringValue"))
							Option(entity.floatValue) must beEqualTo(entity.varNamed("floatValue"))
							Option(entity.doubleValue) must beEqualTo(entity.varNamed("doubleValue"))
							Option(entity.bigDecimalValue) must beEqualTo(entity.varNamed("bigDecimalValue"))
							Option(entity.dateValue) must beEqualTo(entity.varNamed("dateValue"))
							Option(entity.calendarValue) must beEqualTo(entity.varNamed("calendarValue"))
							Option(entity.entityValue) must beEqualTo(entity.varNamed("entityValue"))
						}
					}
				}
			)
		}
		
	}

}