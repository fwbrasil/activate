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
class OrderedQuerySpecs extends ActivateTest {

	"Query framework" should {
		"perform ordered queries" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
							def randomCalendar = {
								val calendar = Calendar.getInstance
								calendar.setTimeInMillis(nextInt)
								calendar
							}
						for (i <- 0 until 30)
							newTestEntity(
								intValue = nextInt,
								longValue = nextLong,
								floatValue = nextFloat,
								doubleValue = nextDouble,
								bigDecimalValue = BigDecimal(nextInt),
								dateValue = new Date(nextInt),
								jodaInstantValue = new DateTime(nextInt),
								calendarValue = randomCalendar,
								stringValue = nextFloat.toString)
					}
						def entities =
							all[ActivateTestEntity].toList
					step {
						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.intValue)
						} must beEqualTo(entities.sortBy(_.intValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.longValue)
						} must beEqualTo(entities.sortBy(_.longValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.floatValue)
						} must beEqualTo(entities.sortBy(_.floatValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.doubleValue)
						} must beEqualTo(entities.sortBy(_.doubleValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.bigDecimalValue)
						} must beEqualTo(entities.sortBy(_.bigDecimalValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.dateValue)
						} must beEqualTo(entities.sortBy(_.dateValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue)
						} must beEqualTo(entities.sortBy(_.jodaInstantValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.calendarValue)
						} must beEqualTo(entities.sortBy(_.calendarValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.stringValue)
						} must beEqualTo(entities.sortBy(_.stringValue))

					}

					step {
						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.intValue asc)
						} must beEqualTo(entities.sortBy(_.intValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.longValue asc)
						} must beEqualTo(entities.sortBy(_.longValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.floatValue asc)
						} must beEqualTo(entities.sortBy(_.floatValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.doubleValue asc)
						} must beEqualTo(entities.sortBy(_.doubleValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.bigDecimalValue asc)
						} must beEqualTo(entities.sortBy(_.bigDecimalValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.dateValue asc)
						} must beEqualTo(entities.sortBy(_.dateValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue asc)
						} must beEqualTo(entities.sortBy(_.jodaInstantValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.calendarValue asc)
						} must beEqualTo(entities.sortBy(_.calendarValue))

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.stringValue asc)
						} must beEqualTo(entities.sortBy(_.stringValue))

					}

					step {
						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.intValue desc)
						} must beEqualTo(entities.sortBy(_.intValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.longValue desc)
						} must beEqualTo(entities.sortBy(_.longValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.floatValue desc)
						} must beEqualTo(entities.sortBy(_.floatValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.doubleValue desc)
						} must beEqualTo(entities.sortBy(_.doubleValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.bigDecimalValue desc)
						} must beEqualTo(entities.sortBy(_.bigDecimalValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.dateValue desc)
						} must beEqualTo(entities.sortBy(_.dateValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue desc)
						} must beEqualTo(entities.sortBy(_.jodaInstantValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.calendarValue desc)
						} must beEqualTo(entities.sortBy(_.calendarValue).reverse)

						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.stringValue desc)
						} must beEqualTo(entities.sortBy(_.stringValue).reverse)

					}

				})
		}

		"perform query with fake order by" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						val a =
							query {
								(entity: ActivateTestEntity) =>
									where(entity isNotNull) select (entity)
							}
						val b =
							query {
								(entity: ActivateTestEntity) =>
									where(entity isNotNull) select (entity) orderBy ()
							}
						a must beEqualTo(b)
					}
				})
		}

		"perform query with fake order by" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
					}
					step {
						val a =
							query {
								(entity: ActivateTestEntity) =>
									where(entity isNotNull) select (entity)
							}
						val b =
							query {
								(entity: ActivateTestEntity) =>
									where(entity isNotNull) select (entity) orderBy ()
							}
						a must beEqualTo(b)
					}
				})
		}

		"perform query with multiple order by" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val expected = List((1, 1), (1, 2), (2, 1), (2, 2), (2, 4), (2, 5), (3, 1), (4, 1))
					step {
						expected.randomize.foreach {
							case (intValue, longValue) =>
								val entity = newEmptyActivateTestEntity
								entity.intValue = intValue
								entity.longValue = longValue
						}
					}
					step {
						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.intValue, entity.longValue)
						}.map(entity => (entity.intValue, entity.longValue)).toList must beEqualTo(expected)
					}
				})
		}

		"perform query with order by null column" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val expected = List("a", "b", "c", null)
					step {
						expected.randomize.foreach(v =>
							newEmptyActivateTestEntity.stringValue = v)
					}
					step {
						query {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.stringValue)
						}.toList.map(_.stringValue) must beEqualTo(expected)
					}
				})
		}
	}

}