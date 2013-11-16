package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.util.RichList._

class Super(param: Int) extends Entity with UUID {
    var intValue = param * 2
}
class Sub(param: Int) extends Super(param) {
    def this() = this(2)
    intValue *= 2
}

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
                        validateResult(select[ActivateTestEntity].where(_.varInitializedInConstructor :== fullStringValue))
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
                        validateResult(select[ActivateTestEntity].where(_.valInitializedInConstructor :== fullStringValue))
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
        "support modified two times in different constructors" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val ((id1, e1), (id2, e2)) =
                        step {
                            (new Sub(1).id -> 4, new Sub().id -> 8)
                        }
                    step {
                        byId[Sub](id1).get.intValue must beEqualTo(e1)
                        byId[Sub](id2).get.intValue must beEqualTo(e2)
                    }
                })
        }
    }

}