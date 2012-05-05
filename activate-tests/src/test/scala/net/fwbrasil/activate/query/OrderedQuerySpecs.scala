package net.fwbrasil.activate.query

import java.util.Date
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import org.joda.time.DateTime
import scala.util.Random
import java.util.Calendar

@RunWith(classOf[JUnitRunner])
class OrderedQuerySpecs extends ActivateTest {

	"Query framework" should {
		"perform ordered queries" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						import Random._
							def randomCalendar = {
								val calendar = Calendar.getInstance
								calendar.setTimeInMillis(nextLong)
								calendar
							}
						for (i <- 0 until 30)
							newTestEntity(
								intValue = nextInt,
								longValue = nextLong,
								floatValue = nextFloat,
								doubleValue = nextDouble,
								bigDecimalValue = BigDecimal(nextInt),
								dateValue = new Date(nextLong),
								jodaInstantValue = new DateTime(nextLong),
								calendarValue = randomCalendar,
								stringValue = nextFloat.toString)
					}
						def entities =
							all[ActivateTestEntity].toList
					step {
						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.intValue)
						} must beEqualTo(entities.sortBy(_.intValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.longValue)
						} must beEqualTo(entities.sortBy(_.longValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.floatValue)
						} must beEqualTo(entities.sortBy(_.floatValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.doubleValue)
						} must beEqualTo(entities.sortBy(_.doubleValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.bigDecimalValue)
						} must beEqualTo(entities.sortBy(_.bigDecimalValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.dateValue)
						} must beEqualTo(entities.sortBy(_.dateValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue)
						} must beEqualTo(entities.sortBy(_.jodaInstantValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.calendarValue)
						} must beEqualTo(entities.sortBy(_.calendarValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.stringValue)
						} must beEqualTo(entities.sortBy(_.stringValue))

					}

					step {
						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.intValue asc)
						} must beEqualTo(entities.sortBy(_.intValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.longValue asc)
						} must beEqualTo(entities.sortBy(_.longValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.floatValue asc)
						} must beEqualTo(entities.sortBy(_.floatValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.doubleValue asc)
						} must beEqualTo(entities.sortBy(_.doubleValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.bigDecimalValue asc)
						} must beEqualTo(entities.sortBy(_.bigDecimalValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.dateValue asc)
						} must beEqualTo(entities.sortBy(_.dateValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue asc)
						} must beEqualTo(entities.sortBy(_.jodaInstantValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.calendarValue asc)
						} must beEqualTo(entities.sortBy(_.calendarValue))

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.stringValue asc)
						} must beEqualTo(entities.sortBy(_.stringValue))

					}

					step {
						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.intValue desc)
						} must beEqualTo(entities.sortBy(_.intValue).reverse)

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.longValue desc)
						} must beEqualTo(entities.sortBy(_.longValue).reverse)

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.floatValue desc)
						} must beEqualTo(entities.sortBy(_.floatValue).reverse)

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.doubleValue desc)
						} must beEqualTo(entities.sortBy(_.doubleValue).reverse)

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.bigDecimalValue desc)
						} must beEqualTo(entities.sortBy(_.bigDecimalValue).reverse)

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.dateValue desc)
						} must beEqualTo(entities.sortBy(_.dateValue).reverse)

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue desc)
						} must beEqualTo(entities.sortBy(_.jodaInstantValue).reverse)

						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.calendarValue desc)
						} must beEqualTo(entities.sortBy(_.calendarValue).reverse)

						executeQuery {
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
							executeQuery {
								(entity: ActivateTestEntity) =>
									where(entity isNotNull) select (entity)
							}
						val b =
							executeQuery {
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
							executeQuery {
								(entity: ActivateTestEntity) =>
									where(entity isNotNull) select (entity)
							}
						val b =
							executeQuery {
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
						executeQuery {
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
						executeQuery {
							(entity: ActivateTestEntity) =>
								where(entity isNotNull) select (entity) orderBy (entity.stringValue)
						}.toList.map(_.stringValue) must beEqualTo(expected)
					}
				})
		}
	}

}