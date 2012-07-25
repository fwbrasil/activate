package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.entity.EntityValidationOption._

class TestValidationEntity(var string1: String) extends Entity {
	var string2 = "s2"
	def string1MustNotBeEmpty =
		invariant {
			string1.nonEmpty
		}
	def string2MustNotBeEmpty =
		invariant {
			string2.nonEmpty
		}
}

@RunWith(classOf[JUnitRunner])
class EntityValidationSpecs extends ActivateTest {

	"Entity validation" should {

		"have onCreate and onWrite as default" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					EntityValidation.getGlobalOptions must beEqualTo(Set(onCreate, onWrite))
					val entityId =
						step {
							new TestValidationEntity("") must throwA[InvariantViolationException]
							new TestValidationEntity("s1").id
						}
						def entity =
							byId[TestValidationEntity](entityId).get
					step {
						val e = entity
						(e.string1 = "") must throwA[InvariantViolationException]
						entity.string1 must beEqualTo("s1")
					}
					step {
						(entity.string2 = "") must throwA[InvariantViolationException]
						entity.string2 must beEqualTo("s2")
					}
					step {
						entity.string1 = "ss1"
						entity.string1 must beEqualTo("ss1")
						entity.string2 = "ss2"
						entity.string2 must beEqualTo("ss2")
					}
				})
		}
	}

}