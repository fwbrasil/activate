package net.fwbrasil.activate.async

import net.fwbrasil.activate.ActivateTest
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class AsyncQuerySpecs extends ActivateTest {

    override def contexts = super.contexts.filter(_.storage.supportsAsync)

    "Async storages" should {

        "perform simple query" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        val future =
                            asyncQuery {
                                (e: ActivateTestEntity) => where(e.id isNotNull) select (e)
                            }
                        val result = Await.result(future, Duration.Inf)
                        result.map(_.id) === List(entityId)
                    }
                })
        }

        "support asyncById query" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val (fullId, emptyId) = step {
                        (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                    }
                    step {
                        await(asyncById[ActivateTestEntity](fullId)) must beSome
                        await(asyncById[ActivateTestEntity](emptyId)) must beSome
                        await(asyncById[ActivateTestEntity]("89889089")) must beNone
                    }
                })
        }

        "support asyncAll" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val (fullId, emptyId) = step {
                        (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                    }
                    step {
                        await(asyncAll[ActivateTestEntity]).size must beEqualTo(3)
                    }
                })
        }
        
        "support asyncSelect[Entity] where" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newFullActivateTestEntity
                        newEmptyActivateTestEntity
                    }
                    step {
                        val future = 
                            asyncSelect[ActivateTestEntity].where(
                            _.intValue :== fullIntValue,
                            _.longValue :== fullLongValue,
                            _.booleanValue :== fullBooleanValue,
                            _.charValue :== fullCharValue,
                            _.stringValue :== fullStringValue,
                            _.floatValue :== fullFloatValue,
                            _.doubleValue :== fullDoubleValue,
                            _.bigDecimalValue :== fullBigDecimalValue,
                            _.dateValue :== fullDateValue,
                            _.calendarValue :== fullCalendarValue,
                            _.entityValue :== fullEntityValue,
                            _.optionValue :== fullOptionValue,
                            _.customNamedValue :== fullStringValue,
                            _.entityWithoutAttributeValue :== fullEntityWithoutAttributeValue,
                            _.caseClassEntityValue :== fullCaseClassEntityValue)
                        await(future).size must beEqualTo(1)
                    }
                })
        }
        
        "support asyncQuery with abstract entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        fullTraitValue1
                        fullTraitValue2
                    }
                    step {
                        await(asyncAll[TraitAttribute]).size must beEqualTo(2)
                    }
                })
        }
    }

    private def await[R](f: Future[R]): R =
        Await.result(f, Duration.Inf)

}