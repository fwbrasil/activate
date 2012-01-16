package net.fwbrasil.activate.query

import java.util.Date
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import org.joda.time.DateTime

@RunWith(classOf[JUnitRunner])
class QuerySpecs extends ActivateTest {

	"Query framework" should {
		"support byId" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val (fullId, emptyId) = step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						byId[ActivateTestEntity](fullId) must beSome
						byId[ActivateTestEntity](emptyId) must beSome
						byId[ActivateTestEntity]("89889089") must beNone
					}
				})
		}

		"support all" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val (fullId, emptyId) = step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						all[ActivateTestEntity].size must beEqualTo(3)
					}
				})
		}

		"support allWhere" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
						newEmptyActivateTestEntity
					}
					step {
						allWhere[ActivateTestEntity](
							_.intValue :== fullIntValue,
							_.booleanValue :== fullBooleanValue,
							_.charValue :== fullCharValue,
							_.stringValue :== fullStringValue,
							_.floatValue :== fullFloatValue,
							_.doubleValue :== fullDoubleValue,
							_.bigDecimalValue :== fullBigDecimalValue,
							_.dateValue :== fullDateValue,
							_.calendarValue :== fullCalendarValue,
							_.entityValue :== fullEntityValue //,
							//							_.enumerationValue :== fullEnumerationValue
							).size must beEqualTo(1)

						allWhere[ActivateTestEntity](
							_.intValue isSome,
							_.booleanValue isSome,
							_.charValue isSome,
							_.stringValue isSome,
							_.floatValue isSome,
							_.doubleValue isSome,
							_.bigDecimalValue isSome,
							_.dateValue isSome,
							_.calendarValue isSome,
							_.byteArrayValue isSome,
							_.entityValue isSome //,
							//							_.enumerationValue isSome
							).size must beEqualTo(1)

						allWhere[ActivateTestEntity](
							_.stringValue isNone,
							_.bigDecimalValue isNone,
							_.dateValue isNone,
							_.calendarValue isNone,
							_.entityValue isNone //,
							//							_.enumerationValue isNone
							).size must beEqualTo(2)

						allWhere[ActivateTestEntity](
							_.entityValue isNone,
							_.charValue :== 'A' //, 
							//							_.enumerationValue isNone
							).headOption must beNone
					}
				})
		}

		"support simple query" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
						newEmptyActivateTestEntity
					}
					step {
						query {
							(e: ActivateTestEntity) => where(e.booleanValue :== true) select (e)
						}.execute.headOption must beSome

						query {
							(e: ActivateTestEntity) => where(e.stringValue :== "hhh") select (e)
						}.execute.headOption must beNone

						query {
							(e: ActivateTestEntity) => where(e.stringValue isNone) select (e)
						}.execute.headOption must beSome

						query {
							(e: ActivateTestEntity) => where(e.stringValue isSome) select (e)
						}.execute.headOption must beSome
					}
				})
		}

		"support query with or" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
						newEmptyActivateTestEntity
					}
					step {
						query {
							(e: ActivateTestEntity) =>
								where((e.booleanValue :== true) :|| (e.booleanValue :== false) :|| (e.booleanValue isNone)) select (e)
						}.execute.size must beEqualTo(3)

						query {
							(e: ActivateTestEntity) =>
								where((e.booleanValue :== true) :|| (e.charValue :== fullCharValue)) select (e)
						}.execute.size must beEqualTo(1)
					}
				})
		}

		"support query with and" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
						newEmptyActivateTestEntity
					}
					step {
						query {
							(e: ActivateTestEntity) =>
								where((e.booleanValue :== true) :&& (e.booleanValue :== false) :&& (e.booleanValue isNone)) select (e)
						}.execute.size must beEqualTo(0)

						query {
							(e: ActivateTestEntity) =>
								where((e.booleanValue isSome) :&& (e.stringValue isSome)) select (e)
						}.execute.size must beEqualTo(1)
					}
				})
		}

		"support query with > and <" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
						newEmptyActivateTestEntity
					}
					step {
						query {
							(e: ActivateTestEntity) =>
								where(e.dateValue :< new Date) select (e)
						}.execute.size must beEqualTo(1)

						query {
							(e: ActivateTestEntity) =>
								where(e.dateValue :> new Date) select (e)
						}.execute.size must beEqualTo(0)

						query {
							(e: ActivateTestEntity) =>
								where(e.intValue :< fullIntValue) select (e)
						}.execute.size must beEqualTo(2)

						query {
							(e: ActivateTestEntity) =>
								where(e.intValue :> fullIntValue) select (e)
						}.execute.size must beEqualTo(0)
					}
				})
		}

		"support query with >= and <=" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
						newEmptyActivateTestEntity
					}
					step {
						query {
							(e: ActivateTestEntity) =>
								where(e.dateValue :<= fullDateValue) select (e)
						}.execute.size must beEqualTo(1)

						query {
							(e: ActivateTestEntity) =>
								where(e.dateValue :>= fullDateValue) select (e)
						}.execute.size must beEqualTo(1)

						query {
							(e: ActivateTestEntity) =>
								where(e.dateValue :>= new Date) select (e)
						}.execute.size must beEqualTo(0)

						query {
							(e: ActivateTestEntity) =>
								where(e.intValue :>= fullIntValue) select (e)
						}.execute.size must beEqualTo(1)

						query {
							(e: ActivateTestEntity) =>
								where(e.intValue :<= fullIntValue) select (e)
						}.execute.size must beEqualTo(3)

						query {
							(e: ActivateTestEntity) =>
								where(e.intValue :<= emptyIntValue) select (e)
						}.execute.size must beEqualTo(2)

						query {
							(e: ActivateTestEntity) =>
								where(e.intValue :>= fullIntValue) select (e)
						}.execute.size must beEqualTo(1)
					}
				})
		}

		"support query with abstract entity" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						all[TraitAttribute].size must beEqualTo(2)
					}
				})
		}

		"support query with nested property" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						println(newFullActivateTestEntity.isInitialized)
					}
					step {
						allWhere[ActivateTestEntity](_.traitValue1.attribute :== "1").size must beEqualTo(1)
					}
				})
		}

	}
}