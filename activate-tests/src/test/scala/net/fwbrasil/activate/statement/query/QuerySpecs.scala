package net.fwbrasil.activate.statement.query

import java.util.Date
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

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
							_.longValue :== fullLongValue,
							_.booleanValue :== fullBooleanValue,
							_.charValue :== fullCharValue,
							_.stringValue :== fullStringValue,
							_.floatValue :== fullFloatValue,
							_.doubleValue :== fullDoubleValue,
							_.bigDecimalValue :== fullBigDecimalValue,
							_.dateValue :== fullDateValue,
							_.calendarValue :== fullCalendarValue,
							_.entityValue :== fullEntityValue,
							//							_.enumerationValue :== fullEnumerationValue
							_.optionValue :== fullOptionValue,
							_.entityWithoutAttributeValue :== fullEntityWithoutAttributeValue,
							_.caseClassEntityValue :== fullCaseClassEntityValue).size must beEqualTo(1)

						allWhere[ActivateTestEntity](
							_.intValue isNotNull,
							_.longValue isNotNull,
							_.booleanValue isNotNull,
							_.charValue isNotNull,
							_.stringValue isNotNull,
							_.floatValue isNotNull,
							_.doubleValue isNotNull,
							_.bigDecimalValue isNotNull,
							_.dateValue isNotNull,
							_.calendarValue isNotNull,
							_.byteArrayValue isNotNull,
							_.entityValue isNotNull,
							//							_.enumerationValue isNotNull
							_.optionValue isNotNull,
							_.entityWithoutAttributeValue :== fullEntityWithoutAttributeValue,
							_.caseClassEntityValue :== fullCaseClassEntityValue).size must beEqualTo(1)

						allWhere[ActivateTestEntity](
							_.stringValue isNull,
							_.bigDecimalValue isNull,
							_.dateValue isNull,
							_.calendarValue isNull,
							_.entityValue isNull,
							//							_.enumerationValue isNull,
							_.entityWithoutAttributeValue isNull,
							_.caseClassEntityValue isNull).size must beEqualTo(2)

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
						val value = true
						query {
							(e: ActivateTestEntity) => where(e.booleanValue :== value) select (e)
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

						query {
							(e: ActivateTestEntity) =>
								where(e.longValue :< fullLongValue) select (e)
						}.execute.size must beEqualTo(2)

						query {
							(e: ActivateTestEntity) =>
								where(e.longValue :> fullLongValue) select (e)
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

		"support like" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							newFullActivateTestEntity.id
						}
						def entity = byId[ActivateTestEntity](entityId).get
						def testLike(stringThatMatch: String, stringThatNotMatch: String, pattern: String) = {
							step {
								entity.stringValue = stringThatMatch
							}
							step {
								allWhere[ActivateTestEntity](_.stringValue like pattern).onlyOne.id must beEqualTo(entityId)
							}
							step {
								entity.stringValue = stringThatNotMatch
							}
							step {
								allWhere[ActivateTestEntity](_.stringValue like pattern).isEmpty must beTrue
							}
						}
					testLike("test", "aaa", "te*")
					testLike("test", "aaa", "te*t")
					testLike("test", "aaa", "te?t")
					testLike("test", "aaa", "????")
				})
		}
		"support regexp" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							newFullActivateTestEntity.id
						}
						def entity = byId[ActivateTestEntity](entityId).get
						def testRegexp(stringThatMatch: String, stringThatNotMatch: String, pattern: String) = {
							step {
								entity.stringValue = stringThatMatch
							}
							step {
								allWhere[ActivateTestEntity](_.stringValue regexp pattern).onlyOne.id must beEqualTo(entityId)
							}
							step {
								entity.stringValue = stringThatNotMatch
							}
							step {
								allWhere[ActivateTestEntity](_.stringValue regexp pattern).isEmpty must beTrue
							}
						}
					testRegexp("my-us3r_n4m3", "th1s1s-wayt00_l0ngt0beausername", "^[a-z0-9_-]{3,16}$")
					testRegexp("myp4ssw0rd", "mypa$$w0rd", "^[a-z0-9_-]{6,18}$")
					testRegexp("#a3c113", "#4d82h4", "^#?([a-f0-9]{6}|[a-f0-9]{3})$")
					testRegexp("my-title-here", "my_title_here", "^[a-z0-9-]+$")
					testRegexp("john@doe.com", "john@doe.something", "^([a-z0-9_\\.-]+)@([\\da-z\\.-]+)\\.([a-z\\.]{2,6})$")
				})
		}
	}
}