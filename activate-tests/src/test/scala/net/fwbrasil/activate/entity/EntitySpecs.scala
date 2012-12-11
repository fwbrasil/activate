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
				})
		}

		"return vars fields" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for (entity <- all[ActivateTestEntity]) {
							entity.vars.toSet.size must beEqualTo(30)
						}
					}
				})
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
							entity.intValue must beEqualTo(entity.varNamed("intValue").unary_!.asInstanceOf[Any])
							entity.longValue must beEqualTo(entity.varNamed("longValue").unary_!.asInstanceOf[Any])
							entity.booleanValue must beEqualTo(entity.varNamed("booleanValue").unary_!.asInstanceOf[Any])
							entity.charValue must beEqualTo(entity.varNamed("charValue").unary_!.asInstanceOf[Any])
							entity.stringValue must beEqualTo(entity.varNamed("stringValue").unary_!.asInstanceOf[Any])
							entity.floatValue must beEqualTo(entity.varNamed("floatValue").unary_!.asInstanceOf[Any])
							entity.doubleValue must beEqualTo(entity.varNamed("doubleValue").unary_!.asInstanceOf[Any])
							entity.bigDecimalValue must beEqualTo(entity.varNamed("bigDecimalValue").unary_!.asInstanceOf[Any])
							entity.dateValue must beEqualTo(entity.varNamed("dateValue").unary_!.asInstanceOf[Any])
							entity.calendarValue must beEqualTo(entity.varNamed("calendarValue").unary_!.asInstanceOf[Any])
							entity.entityValue must beEqualTo(entity.varNamed("entityValue").unary_!.asInstanceOf[Any])
							entity.enumerationValue must beEqualTo(entity.varNamed("enumerationValue").unary_!.asInstanceOf[Any])
						}
					}
				})
		}

		"handle case class copy" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							CaseClassEntity(fullStringValue, fullEntityValue, fullEntityWithoutAttributeValue).id
						}
					val copyId =
						step {
							val entity = byId[CaseClassEntity](entityId).get
							entity.copy(entityValue = emptyEntityValue).id
						}
					step {
						entityId must not be equalTo(copyId)
						val entity = byId[CaseClassEntity](entityId).get
						val copy = byId[CaseClassEntity](copyId).get
						entity must not be equalTo(copy)
						entity.entityValue must be equalTo (fullEntityValue)
						copy.entityValue must be equalTo (emptyEntityValue)
					}
					step {
						all[CaseClassEntity].map(_.id).toSet must be equalTo (Set(entityId, copyId))
					}
				})
		}

	}

}