package net.fwbrasil.activate.async

import net.fwbrasil.activate.ActivateTest
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration

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
    }

}