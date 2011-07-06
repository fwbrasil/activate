package net.fwbrasil.activate.query

import java.util.Date
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest

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
				}
			)
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
				}
			)
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
							_.intValue :== fullIntValue.get,
							_.booleanValue :== fullBooleanValue.get,
							_.charValue :== fullCharValue.get,
							_.stringValue :== fullStringValue.get,
							_.floatValue :== fullFloatValue.get,
							_.doubleValue :== fullDoubleValue.get,
							_.bigDecimalValue :== fullBigDecimalValue.get,
							_.dateValue :== fullDateValue.get,
							_.calendarValue :== fullCalendarValue.get,
							_.entityValue :== fullEntityValue.get
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
							_.entityValue isSome
						).size must beEqualTo(1)
						
						allWhere[ActivateTestEntity](
							_.intValue isNone,
							_.booleanValue isNone,
							_.charValue isNone,
							_.stringValue isNone,
							_.floatValue isNone,
							_.doubleValue isNone,
							_.bigDecimalValue isNone,
							_.dateValue isNone,
							_.calendarValue isNone,
							_.byteArrayValue isNone,
							_.entityValue isNone
						).size must beEqualTo(2)
						
						allWhere[ActivateTestEntity](_.booleanValue isNone, _.charValue :== 'A').headOption must beNone
					}
				}
			)
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
							(e: ActivateTestEntity) => where(e.booleanValue :== true) select(e)
						}.execute.headOption must beSome
						
						query {
							(e: ActivateTestEntity) => where(e.stringValue :== "hhh") select(e)
						}.execute.headOption must beNone
						
						query {
							(e: ActivateTestEntity) => where(e.stringValue isNone) select(e)
						}.execute.headOption must beSome
						
						query {
							(e: ActivateTestEntity) => where(e.stringValue isSome) select(e)
						}.execute.headOption must beSome
					}
				}
			)
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
								where((e.booleanValue :== true) :|| (e.booleanValue :== false) :|| (e.booleanValue isNone)) select(e)
						}.execute.size must beEqualTo(3)
						
						query {
							(e: ActivateTestEntity) => 
								where((e.booleanValue :== true) :|| (e.booleanValue isSome)) select(e)
						}.execute.size must beEqualTo(1)
					}
				}
			)
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
								where((e.booleanValue :== true) :&& (e.booleanValue :== false) :&& (e.booleanValue isNone)) select(e)
						}.execute.size must beEqualTo(0)
						
						query {
							(e: ActivateTestEntity) => 
								where((e.booleanValue isSome) :&& (e.stringValue isSome)) select(e)
						}.execute.size must beEqualTo(1)
					}
				}
			)
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
								where(e.dateValue :< new Date) select(e)
						}.execute.size must beEqualTo(1)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.dateValue :> new Date) select(e)
						}.execute.size must beEqualTo(0)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.intValue :< 10) select(e)
						}.execute.size must beEqualTo(1)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.intValue :> 10) select(e)
						}.execute.size must beEqualTo(0)
					}
				}
			)
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
								where(e.dateValue :<= fullDateValue.get) select(e)
						}.execute.size must beEqualTo(1)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.dateValue :>= fullDateValue.get) select(e)
						}.execute.size must beEqualTo(1)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.dateValue :>= new Date) select(e)
						}.execute.size must beEqualTo(0)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.intValue :>= 1) select(e)
						}.execute.size must beEqualTo(1)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.intValue :<= 1) select(e)
						}.execute.size must beEqualTo(1)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.intValue :<= 10) select(e)
						}.execute.size must beEqualTo(1)
						
						query {
							(e: ActivateTestEntity) => 
								where(e.intValue :>= 10) select(e)
						}.execute.size must beEqualTo(0)
					}
				}
			)
		}
	}
}