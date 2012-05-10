package net.fwbrasil.activate.statement.query

import java.util.Date
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EntityListSpecs extends ActivateTest {

	"Query framework" should {
		"support empty entity list" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val id =
						step {
							newFullActivateTestEntity.id
						}
					step {
						val entity = byId[ActivateTestEntity](id).get
						val caseClassEntities =
							allWhere[CaseClassEntity](_.entityValue :== entity)
						caseClassEntities must beEqualTo(entity.caseClassList.toList)
					}
				})
		}
		"support entity list" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val id =
						step {
							val entity = newFullActivateTestEntity
							CaseClassEntity("a", entity, null)
							entity.id
						}
					step {
						val entity = byId[ActivateTestEntity](id).get
						val caseClassEntities =
							allWhere[CaseClassEntity](_.entityValue :== entity)
						caseClassEntities must beEqualTo(entity.caseClassList.toList)
					}
				})
		}
	}
}