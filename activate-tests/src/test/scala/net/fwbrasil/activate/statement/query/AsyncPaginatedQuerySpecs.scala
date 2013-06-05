package net.fwbrasil.activate.statement.query

import java.util.Calendar
import java.util.Date
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.ActivateTest
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import scala.util.Random.nextDouble
import scala.util.Random.nextFloat
import scala.util.Random.nextInt
import scala.util.Random.nextLong
import net.fwbrasil.activate.ActivateTestContext
import scala.concurrent.Await
import scala.concurrent.duration.Duration

@RunWith(classOf[JUnitRunner])
class AsyncPaginatedQuerySpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filter(!_.isInstanceOf[OneTransaction])

    override def contexts =
        super.contexts.filter(_.storage.supportsAsync)

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
                        def test(pageSize: Int) =
                            asyncTransactionalChain {
                                implicit ctx =>
                                    asyncPaginatedQuery {
                                        (e: ActivateTestEntity) => where(e isNotNull) select (e) orderBy (e.intValue)
                                    }.navigator(pageSize).map {
                                        pagination =>
                                            val expectedNumberOfPages =
                                                (numberOfEntities / pageSize) + (if (numberOfEntities % pageSize > 0) 1 else 0)
                                            pagination.numberOfPages must beEqualTo(expectedNumberOfPages)
                                            val expectedPages = numbers.grouped(pageSize).toList
                                            pagination.page(-1) must throwA[IndexOutOfBoundsException]
                                            if (pagination.numberOfPages > 0) {
                                                pagination.page(0)
                                                pagination.page(expectedNumberOfPages - 1)
                                            } else
                                                pagination.page(0) must throwA[IndexOutOfBoundsException]
                                            pagination.page(expectedNumberOfPages) must throwA[IndexOutOfBoundsException]
                                    }(ctx)
                            }
                        for (i <- 1 until 6)
                            Await.result(test(pageSize = i), Duration.Inf)

                    })
            }
        }
    }

}