package net.fwbrasil.activate.async

import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import net.fwbrasil.activate.ActivateTestContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.Awaitable
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AsyncPaginatedQuerySpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filter(!_.isInstanceOf[OneTransaction])

    "Query framework" should {
        "support paginated queries" in {
            for (numberOfEntities <- List(0, 2, 60)) {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val numbers = (0 until numberOfEntities).toList
                        transactional {
                            numbers.foreach(newEmptyActivateTestEntity.intValue = _)
                        }
                        def test(pageSize: Int) = {
                            val navigator =
                                await(asyncTransactionalChain {
                                    implicit ctx =>
                                        asyncPaginatedQuery {
                                            (e: ActivateTestEntity) => where(e isNotNull) select (e) orderBy (e.intValue)
                                        }.navigator(pageSize)
                                })
                            val expectedNumberOfPages =
                                (numberOfEntities / pageSize) + (if (numberOfEntities % pageSize > 0) 1 else 0)
                            navigator.numberOfPages must beEqualTo(expectedNumberOfPages)
                            def page(n: Int) =
                                asyncTransactionalChain {
                                    implicit ctx =>
                                        navigator.page(n)
                                }
                            await(page(-1)) must throwA[IndexOutOfBoundsException]
                            if (navigator.numberOfPages > 0) {
                                await(page(0))
                                await(page(expectedNumberOfPages - 1))
                            } else
                                await(page(0)) must throwA[IndexOutOfBoundsException]
                            await(page(expectedNumberOfPages)) must throwA[IndexOutOfBoundsException]
                        }
                        for (i <- 1 until 6)
                            test(pageSize = i)

                    })
            }
        }
    }

    private def await[T](v: Awaitable[T]) =
        Await.result(v, Duration.Inf)

}