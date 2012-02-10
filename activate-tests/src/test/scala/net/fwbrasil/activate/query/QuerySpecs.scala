package net.fwbrasil.activate.query

import java.util.Date
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
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
							_.intValue isNotNull,
							_.booleanValue isNotNull,
							_.charValue isNotNull,
							_.stringValue isNotNull,
							_.floatValue isNotNull,
							_.doubleValue isNotNull,
							_.bigDecimalValue isNotNull,
							_.dateValue isNotNull,
							_.calendarValue isNotNull,
							_.byteArrayValue isNotNull,
							_.entityValue isNotNull //,
							//							_.enumerationValue isNotNull
							).size must beEqualTo(1)

						allWhere[ActivateTestEntity](
							_.stringValue isNull,
							_.bigDecimalValue isNull,
							_.dateValue isNull,
							_.calendarValue isNull,
							_.entityValue isNull //,
							//							_.enumerationValue isNull
							).size must beEqualTo(2)

						allWhere[ActivateTestEntity](
							_.entityValue isNull,
							_.charValue :== 'A' //, 
							//							_.enumerationValue isNull
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
							(e: ActivateTestEntity) => where(e.stringValue isNull) select (e)
						}.execute.headOption must beSome

						query {
							(e: ActivateTestEntity) => where(e.stringValue isNotNull) select (e)
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
								where((e.booleanValue :== true) :|| (e.booleanValue :== false) :|| (e.booleanValue isNull)) select (e)
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
								where((e.booleanValue :== true) :&& (e.booleanValue :== false) :&& (e.booleanValue isNull)) select (e)
						}.execute.size must beEqualTo(0)

						query {
							(e: ActivateTestEntity) =>
								where((e.booleanValue isNotNull) :&& (e.stringValue isNotNull)) select (e)
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
						fullTraitValue1
						fullTraitValue2
					}
					step {
						all[TraitAttribute].size must beEqualTo(2)
					}
				})
		}

		"support query with nested property" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					if (step.ctx.storage.supportComplexQueries) {
						step {
							newFullActivateTestEntity
						}
						step {
							val a = all[ActivateTestEntity]
							allWhere[ActivateTestEntity](_.traitValue1.attribute :== "1").size must beEqualTo(1)
						}
					}
				})
		}

		"support queries about more than one entity" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					if (step.ctx.storage.supportComplexQueries) {
						step {
							newFullActivateTestEntity
						}
						step {
							val (fullEntity, entityValue) =
								(executeQuery {
									(entity1: ActivateTestEntity, entity2: ActivateTestEntity) => where(entity1.entityValue :== entity2) select (entity1, entity2)
								}).onlyOne
							fullEntity.entityValue must beEqualTo(entityValue)
						}
						step {
							val (fullEntity, traitAttribute) =
								(executeQuery {
									(entity1: ActivateTestEntity, traitAttribute: TraitAttribute) => where(entity1.traitValue1 :== traitAttribute) select (entity1, traitAttribute)
								}).onlyOne
							fullEntity.traitValue1 must beEqualTo(traitAttribute)
						}
					}
				})
		}

	}
}