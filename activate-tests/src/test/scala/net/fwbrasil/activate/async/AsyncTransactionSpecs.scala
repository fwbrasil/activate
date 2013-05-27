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
class AsyncTransactionSpecs extends ActivateTest {

    override def contexts = super.contexts.filter(_.storage.supportsAsync)

    "Async storages" should {

        "support asyncTransactional" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        
                    }
                })
        }

    }

    private def await[R](f: Future[R]): R =
        Await.result(f, Duration.Inf)

}