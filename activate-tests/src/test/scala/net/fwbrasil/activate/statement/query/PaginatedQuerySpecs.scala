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
import org.specs2.execute.PendingUntilFixed

@RunWith(classOf[JUnitRunner])
class PaginatedQuerySpecs extends ActivateTest {

    "Query framework" should {
//        "support paginated queries" in {
//            for (numberOfEntities <- List(0, 2, 7, 60)) {
//                activateTest(
//                    (step: StepExecutor) => {
//                        import step.ctx._
//                        val numbers = (0 until numberOfEntities).toList
//                        step {
//                            numbers.foreach(newEmptyActivateTestEntity.intValue = _)
//                        }
//                        step {
//                            def test(pageSize: Int) = {
//                                val pagination =
//                                    paginatedQuery {
//                                        (e: ActivateTestEntity) => where() select (e) orderBy (e.intValue)
//                                    }.navigator(pageSize)
//                                val expectedNumberOfPages =
//                                    (numberOfEntities / pageSize) + (if (numberOfEntities % pageSize > 0) 1 else 0)
//                                pagination.numberOfPages must beEqualTo(expectedNumberOfPages)
//                                pagination.page(-1) must throwA[IndexOutOfBoundsException]
//                                if (pagination.numberOfPages > 0) {
//                                    pagination.page(0) === pagination.firstPage
//                                    pagination.page(expectedNumberOfPages - 1) === pagination.lastPage
//                                } else
//                                    pagination.page(0) must throwA[IndexOutOfBoundsException]
//                                pagination.page(expectedNumberOfPages) must throwA[IndexOutOfBoundsException]
//
//                                val expectedPages =
//                                    numbers.grouped(pageSize).toList
//                                val actualPages =
//                                    for (i <- 0 until expectedNumberOfPages) yield {
//                                        pagination.page(i).map(_.intValue)
//                                    }
//                                expectedPages === actualPages.toList
//                            }
//                            for (i <- 1 until 6)
//                                test(pageSize = i)
//                        }
//                    })
//            }
//            ok
//        }

        "support polymorfic paginated queries" in {
            for (numberOfEntities <- List(0, 2, 7, 60)) {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            new TraitAttribute1("a")
                            new TraitAttribute2("b")
                            new TraitAttribute2("c")
                            new TraitAttribute1("d")
                            new TraitAttribute1("e")
                            new TraitAttribute1("f")
                            new TraitAttribute2("g")
                            new TraitAttribute1("h")
                        }
                        step {
                            for (pageSize <- 1 until 6) {
                                val navigator =
                                    paginatedQuery {
                                        (e: TraitAttribute) => where() select (e) orderBy (e.attribute)
                                    }.navigator(pageSize)
                                val expected =
                                    all[TraitAttribute].sortBy(_.attribute).grouped(pageSize).toList
                                val actualPages =
                                    for (i <- 0 until navigator.numberOfPages) yield navigator.page(i)
                                actualPages.toList === expected
                            }
                        }
                    })
            }
            ok
        }
    }

}