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

    override def executors(ctx: ActivateTestContext) = List(MultipleAsyncTransactions(ctx))

    "Async storages" should {

        "support fully async transaction" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    transactional {
                        newEmptyActivateTestEntity
                    }
                    await(asyncTransactionalChain {
                        implicit ctx =>
                            asyncAll[ActivateTestEntity].map {
                                _.head.intValue = 1983
                            }(ctx)
                    })
                    transactional {
                        all[ActivateTestEntity].map(_.intValue) === List(1983)
                    }
                    ok
                })
        }

    }

    private def await[R](f: Future[R]): R =
        Await.result(f, Duration.Inf)

}