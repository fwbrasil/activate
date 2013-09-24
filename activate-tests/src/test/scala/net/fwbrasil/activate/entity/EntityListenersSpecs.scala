package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.lift.EntityForm
import java.util.NoSuchElementException

@RunWith(classOf[JUnitRunner])
class EntityListenersSpecs extends ActivateTest {

    "Entity listeners" should {
        "be notified" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        ActivateTestEntity.onModifyFloatCallback =
                            (oldValue: Float, newValue: Float) => {
                                throw new IllegalStateException("Should not callback on constructor")
                            }
                    }
                    val entityId =
                        step {
                            (new ActivateTestEntity(0)).id
                        }
                    var called = false
                    step {
                        ActivateTestEntity.onModifyFloatCallback =
                            (oldValue: Float, newValue: Float) => {
                                if(called)
                                    throw new IllegalStateException("Called twice!")
                                oldValue === emptyFloatValue
                                newValue === fullFloatValue
                                called = true
                            }
                    }
                    step {
                        all[ActivateTestEntity].onlyOne.floatValue = fullFloatValue
                        called must beTrue
                    }

                })
        }
    }

}