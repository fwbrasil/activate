package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.entity.EntityValidationOption._
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.entity.id.UUID

class EntityValidationPrePostConditionsSpecsEntity extends Entity {
    var string = "s"
    def methodWithPreCondition(f: => Unit) =
        preCondition(string == "s") {
            f
        }

    def methodWithPostCondition(f: => Unit) = {
        f
    } postCondition (string == "s")
}

@RunWith(classOf[JUnitRunner])
class EntityValidationPrePostConditionsSpecs extends ActivateTest {

    "Entity validation framework" should {
        "support preCondition" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val entity = new EntityValidationPrePostConditionsSpecsEntity
                        var called = false
                        entity.methodWithPreCondition(called = true)
                        called must beTrue
                    }
                    step {
                        val entity = new EntityValidationPrePostConditionsSpecsEntity
                        var called = false
                        entity.string = "a"
                        entity.methodWithPreCondition(called = true) must throwA[PreCondidionViolationException]
                        called must beFalse
                    }
                })
        }

        "support postCondition" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val entity = new EntityValidationPrePostConditionsSpecsEntity
                        var called = false
                        entity.methodWithPostCondition(called = true)
                        called must beTrue
                    }
                    step {
                        val entity = new EntityValidationPrePostConditionsSpecsEntity
                        var called = false
                        entity.string = "a"
                        entity.methodWithPostCondition(called = true) must throwA[PostCondidionViolationException]
                        called must beTrue
                    }
                })
        }

    }
}