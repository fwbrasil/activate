package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.lift.EntityForm
import java.util.NoSuchElementException
import net.fwbrasil.activate.memoryContext

@RunWith(classOf[JUnitRunner])
class EntityListenersSpecs extends ActivateTest {

    "Listeners" should {

        "be notified of lifecycle events" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx != memoryContext &&
                        (step.isInstanceOf[MultipleTransactionsWithReinitialize] ||
                            step.isInstanceOf[MultipleTransactionsWithReinitializeAndSnapshot])) {
                        var events = List[String]()
                        ActivateTestEntity.lifecycleCallback = (event: String) => events ++= List(event)
                        step {
                            newEmptyActivateTestEntity
                        }
                        step {
                            val entity = all[ActivateTestEntity].onlyOne
                            entity.intValue
                            entity.delete
                        }
                        step {
                            events === List(
                                "beforeConstruct", "insideConstructor",
                                "afterConstruct", "beforeInitialize",
                                "afterInitialize", "beforeDelete",
                                "afterDelete")
                        }
                    }
                })
        }

        "be notified of vars modifications" in {
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
                                if (called)
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