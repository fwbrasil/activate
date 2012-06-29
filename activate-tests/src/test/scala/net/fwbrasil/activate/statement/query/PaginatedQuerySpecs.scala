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

@RunWith(classOf[JUnitRunner])
class PaginatedQuerySpecs extends ActivateTest {

	"Query framework" should {
		"support paginated queries" in {
			for (numberOfEntities <- List(0, 2, 60)) {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						val numbers = (0 until numberOfEntities).toList
						step {
							numbers.foreach(newFullActivateTestEntity.intValue = _)
						}
						step {
								def test(pageSize: Int) = {
									val pagination =
										paginatedQuery {
											(e: ActivateTestEntity) => where(e isNotNull) select (e) orderBy (e.intValue)
										}.navigator(pageSize)
									val expectedNumberOfPages =
										(numberOfEntities / pageSize) + (if (numberOfEntities % pageSize > 0) 1 else 0)
									pagination.numberOfPages must beEqualTo(expectedNumberOfPages)
									val expectedPages = numbers.grouped(pageSize).toList
									pagination.toList.map(_.map(_.intValue)) must beEqualTo(expectedPages)
									pagination.page(-1) must throwA[IndexOutOfBoundsException]
									if (pagination.numberOfPages > 0) {
										pagination.page(0)
										pagination.page(expectedNumberOfPages - 1)
									} else
										pagination.page(0) must throwA[IndexOutOfBoundsException]
									pagination.page(expectedNumberOfPages) must throwA[IndexOutOfBoundsException]
								}
							for (i <- 1 until 6)
								test(pageSize = i)
						}
					})
			}
			true must beTrue
		}
	}

}