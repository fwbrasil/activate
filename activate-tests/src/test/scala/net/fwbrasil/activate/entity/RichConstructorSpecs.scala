package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.util.RichList._

@RunWith(classOf[JUnitRunner])
class RichConstructorSpecs extends ActivateTest {

	val dummyString = "test"

	"Entity constructor" should {
		"support var defined in constructor" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newEmptyActivateTestEntity
					}
					step {
							def validateResult(result: List[ActivateTestEntity]) =
								result.onlyOne.varInitializedInConstructor must beEqualTo(fullStringValue)
						validateResult(all[ActivateTestEntity])
						validateResult(allWhere[ActivateTestEntity](_.varInitializedInConstructor :== fullStringValue))
						validateResult(query {
							(e: ActivateTestEntity) => where(e.varInitializedInConstructor :== fullStringValue) select (e)
						})
					}
					step {
						all[ActivateTestEntity].onlyOne.varInitializedInConstructor = dummyString
					}
					step {
						all[ActivateTestEntity].onlyOne.varInitializedInConstructor must beEqualTo(dummyString)
					}
				})
		}
		"support val defined in constructor" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newEmptyActivateTestEntity
					}
					step {
							def validateResult(result: List[ActivateTestEntity]) =
								result.onlyOne.valInitializedInConstructor must beEqualTo(fullStringValue)
						validateResult(all[ActivateTestEntity])
						validateResult(allWhere[ActivateTestEntity](_.valInitializedInConstructor :== fullStringValue))
						validateResult(query {
							(e: ActivateTestEntity) => where(e.valInitializedInConstructor :== fullStringValue) select (e)
						})
					}
				})
		}
		"support val calcultated in constructor" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val (entityId1, entityId2) =
						step {
							val entity1 = newTestEntity(intValue = fullIntValue)
							val entity2 = new ActivateTestEntity(fullIntValue)
							(entity1.id, entity2.id)
						}
					step {
						byId[ActivateTestEntity](entityId1).get.calculatedInConstructor must beEqualTo(fullIntValue * 2)
						byId[ActivateTestEntity](entityId2).get.calculatedInConstructor must beEqualTo(fullIntValue * 4)
					}
				})
		}
	}

}