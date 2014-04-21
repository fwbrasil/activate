package net.fwbrasil.activate.statement.query

import java.util.Date
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.mongoContext
import net.fwbrasil.activate.polyglotContext
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.asyncMongoContext
import net.fwbrasil.activate.mysqlContext
import net.fwbrasil.activate.migration.StorageVersion
import net.fwbrasil.activate.multipleVms.CustomEncodedEntityValue

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

        "support select[Entity] where" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newFullActivateTestEntity
                        newEmptyActivateTestEntity
                    }
                    step {
                        select[ActivateTestEntity].where(
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
                            _.customNamedValue :== fullStringValue,
                            _.entityWithoutAttributeValue :== fullEntityWithoutAttributeValue,
                            _.caseClassEntityValue :== fullCaseClassEntityValue).size must beEqualTo(1)

                        select[ActivateTestEntity].where(
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

                        select[ActivateTestEntity].where(
                            _.stringValue isNull,
                            _.bigDecimalValue isNull,
                            _.dateValue isNull,
                            _.calendarValue isNull,
                            _.entityValue isNull,
                            //							_.enumerationValue isNull,
                            _.entityWithoutAttributeValue isNull,
                            _.caseClassEntityValue isNull).size must beEqualTo(2)

                        select[ActivateTestEntity].where(
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
                        }.headOption must beSome

                        query {
                            (e: ActivateTestEntity) => where(e.stringValue :!= "B") select (e)
                        }.headOption must beSome

                        query {
                            (e: ActivateTestEntity) => where(e.stringValue :== "hhh") select (e)
                        }.headOption must beNone

                        query {
                            (e: ActivateTestEntity) => where(e.stringValue isNull) select (e)
                        }.headOption must beSome

                        query {
                            (e: ActivateTestEntity) => where(e.stringValue isNotNull) select (e)
                        }.headOption must beSome

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
                        }.size must beEqualTo(3)

                        query {
                            (e: ActivateTestEntity) =>
                                where((e.booleanValue :== true) :|| (e.charValue :== fullCharValue)) select (e)
                        }.size must beEqualTo(1)
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
                        }.size must beEqualTo(0)

                        query {
                            (e: ActivateTestEntity) =>
                                where((e.booleanValue isNotNull) :&& (e.stringValue isNotNull)) select (e)
                        }.size must beEqualTo(1)
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
                        }.size must beEqualTo(1)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.dateValue :> new Date) select (e)
                        }.size must beEqualTo(0)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.intValue :< fullIntValue) select (e)
                        }.size must beEqualTo(2)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.intValue :> fullIntValue) select (e)
                        }.size must beEqualTo(0)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.longValue :< fullLongValue) select (e)
                        }.size must beEqualTo(2)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.longValue :> fullLongValue) select (e)
                        }.size must beEqualTo(0)
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
                        }.size must beEqualTo(1)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.dateValue :>= fullDateValue) select (e)
                        }.size must beEqualTo(1)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.dateValue :>= new Date) select (e)
                        }.size must beEqualTo(0)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.intValue :>= fullIntValue) select (e)
                        }.size must beEqualTo(1)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.intValue :<= fullIntValue) select (e)
                        }.size must beEqualTo(3)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.intValue :<= emptyIntValue) select (e)
                        }.size must beEqualTo(2)

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.intValue :>= fullIntValue) select (e)
                        }.size must beEqualTo(1)
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
                    if (step.ctx.storage.supportsQueryJoin) {
                        step {
                            newFullActivateTestEntity
                        }
                        step {
                            select[ActivateTestEntity].where(_.traitValue1.attribute :== "1").size must beEqualTo(1)
                        }
                    }
                })
        }

        "support queries about more than one entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx.storage.supportsQueryJoin) {
                        step {
                            newFullActivateTestEntity
                        }
                        step {
                            val (fullEntity, entityValue) =
                                (query {
                                    (entity1: ActivateTestEntity, entity2: ActivateTestEntity) => where(entity1.entityValue :== entity2) select (entity1, entity2)
                                }).onlyOne
                            fullEntity.entityValue must beEqualTo(entityValue)
                        }
                        step {
                            val (fullEntity, traitAttribute) =
                                (query {
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
                    if (step.ctx.storage.supportsRegex) { //UnsupportedOperationException
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
                                select[ActivateTestEntity].where(_.stringValue like pattern).onlyOne.id must beEqualTo(entityId)
                            }
                            step {
                                entity.stringValue = stringThatNotMatch
                            }
                            step {
                                select[ActivateTestEntity].where(_.stringValue like pattern).isEmpty must beTrue
                            }
                        }
                        testLike("test", "aaa", "te*")
                        testLike("test", "aaa", "te*t")
                        testLike("test", "aaa", "te?t")
                        testLike("test", "aaa", "????")
                    }
                })
        }
        "support regexp" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx.storage.supportsRegex) { //UnsupportedOperationException
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
                                select[ActivateTestEntity].where(_.stringValue regexp pattern).onlyOne.id must beEqualTo(entityId)
                            }
                            step {
                                entity.stringValue = stringThatNotMatch
                            }
                            step {
                                select[ActivateTestEntity].where(_.stringValue regexp pattern).isEmpty must beTrue
                            }
                        }
                        testRegexp("my-us3r_n4m3", "th1s1s-wayt00_l0ngt0beausername", "^[a-z0-9_-]{3,16}$")
                        testRegexp("myp4ssw0rd", "mypa$$w0rd", "^[a-z0-9_-]{6,18}$")
                        testRegexp("#a3c113", "#4d82h4", "^#?([a-f0-9]{6}|[a-f0-9]{3})$")
                        testRegexp("my-title-here", "my_title_here", "^[a-z0-9-]+$")
                        testRegexp("john@doe.com", "john@doe.something", "^([a-z0-9_\\.-]+)@([\\da-z\\.-]+)\\.([a-z\\.]{2,6})$")
                        testRegexp("test1string", "teststring", "^.*[0-9].*$")
                        testRegexp("test1str2ing", "teststring", "^.*[0-9].*$")
                        testRegexp("test12str3ing", "teststring", "^.*[0-9].*$")
                    }
                })
        }

        "support property path ending with id" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx.storage.supportsQueryJoin) {
                        val (entityId, entityValueId) =
                            step {
                                val entity = newFullActivateTestEntity
                                (entity.id, entity.entityValue.id)
                            }
                        step {
                            select[ActivateTestEntity].where(_.entityValue.id :== entityValueId).onlyOne.id must beEqualTo(entityId)
                        }
                    }
                })
        }

        "support select entity id" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx.storage.supportsQueryJoin) {
                        val entityValueId =
                            step {
                                val entity = newEmptyActivateTestEntity
                                entity.stringValue = "test"
                                entity.id
                            }
                        step {
                            val result = query {
                                (e: ActivateTestEntity) => where(e isNotNull) select (e.id, e.intValue, e.bigDecimalValue, e.stringValue)
                            }
                            result.onlyOne must beEqualTo((entityValueId, 0, null, "test"))
                        }
                    }
                })
        }

        "not use deleted entities" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity.delete
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        all[ActivateTestEntity].onlyOne.id must beEqualTo(entityId)
                    }
                })
        }

        "support count query" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                        newEmptyActivateTestEntity
                    }
                    step {
                        query {
                            (e: ActivateTestEntity) => where(e isNotNull) select (1)
                        }.sum must beEqualTo(2)
                    }
                })
        }
        //        "select simple values" in {
        //            activateTest(
        //                (step: StepExecutor) => {
        //                    import step.ctx._
        //                    step {
        //                        newFullActivateTestEntity
        //                    }
        //                    step {
        //                        newFullActivateTestEntity
        //                        query {
        //                            (e: ActivateTestEntity) => where(e.stringValue :== fullStringValue) select ("a")
        //                        } must beEqualTo(List("a", "a"))
        //                    }
        //                })
        //        }

        "support query entity hierarchy involving option relation" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val (bossId, worker1Id, worker2Id) =
                        step {
                            val boss = new Employee("Boss", None)
                            val worker1 = new Employee("Worker1", Some(boss))
                            val worker2 = new Employee("Worker2", Some(boss))
                            (boss.id, worker1.id, worker2.id)
                        }
                    step {
                        val boss = byId[Employee](bossId).get
                        val worker1 = byId[Employee](worker1Id).get
                        val worker2 = byId[Employee](worker2Id).get

                        worker1.subordinates must beEmpty
                        worker1.supervisor must beSome(boss)

                        worker2.subordinates must beEmpty
                        worker2.supervisor must beSome(boss)

                        boss.supervisor must beNone
                        (boss.subordinates.map(_.id).toSet) must beEqualTo(Set(worker1Id, worker2Id))
                    }
                })
        }

        "support select [Entity] where (criteria)" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newFullActivateTestEntity
                    }
                    step {
                        select[ActivateTestEntity] where (_.stringValue :== fullStringValue)
                    }
                })
        }

        "support normalized query with entity source used on select" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx != polyglotContext && step.ctx.storage.supportsQueryJoin) {
                        step {
                            newFullActivateTestEntity
                        }
                        step {
                            query {
                                (a: ActivateTestEntity, b: CaseClassEntity) => where(b.entityValue.stringValue :== fullStringValue) select (a)
                            }
                            select[ActivateTestEntity] where (_.stringValue :== fullStringValue)
                        }
                    }
                })

        }

        "support toUpperCase" in {
            activateTest(
                (step: StepExecutor) => {
                    if (step.ctx != mongoContext && step.ctx != asyncMongoContext) {
                        import step.ctx._
                        val string = "s"
                        step {
                            newEmptyActivateTestEntity.stringValue = string
                        }
                        step {
                            val result = select[ActivateTestEntity].where(e => toUpperCase(e.stringValue) :== string.toUpperCase)
                            result.size === 1
                        }
                    }
                })

        }

        "support toLowerCase" in {
            activateTest(
                (step: StepExecutor) => {
                    if (step.ctx != mongoContext && step.ctx != asyncMongoContext) {
                        import step.ctx._
                        val string = "S"
                        step {
                            newEmptyActivateTestEntity.stringValue = string
                        }
                        step {
                            val result = select[ActivateTestEntity].where(e => toLowerCase(e.stringValue) :== string.toLowerCase)
                            result.size === 1
                        }
                    }
                })

        }

        "support user function" in {
            activateTest(
                (step: StepExecutor) => {
                    val excludedContexts = Seq(mongoContext, asyncMongoContext, mysqlContext)
                    if (!excludedContexts.contains(step.ctx)) {
                        import step.ctx._
                        val string1 = "-1"
                        val string2 = "-2"
                        import net.fwbrasil.activate.statement.StatementSelectValue

                        object absFunc extends UserFunction[String, Int] {
                          def apply(value: String) = value.toInt.abs
                          def toStorage(value: String) = s"ABS(CAST($value as INTEGER))"
                        }

                        def abs(value: String)(implicit tval1: (=> String) => StatementSelectValue) = toUserFunction(absFunc)(value)
                        step {
                            newEmptyActivateTestEntity.stringValue = string1
                            newEmptyActivateTestEntity.stringValue = string2
                        }
                        step {
                            val result = select[ActivateTestEntity].where(e => toUserFunction(absFunc)(e.stringValue) :== string1.toInt.abs)
                            result.size === 1
                        }
                        step {
                            val result = select[ActivateTestEntity].where(e => abs(e.stringValue) :>= string1.toInt.abs)
                            result.size === 2
                        }
                    }
                })
        }

        "support query with many results" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entities = 1000
                    step {
                        for (i <- 0 until entities)
                            new TraitAttribute1("1")
                    }
                    step {
                        all[TraitAttribute1].size === entities
                    }
                })
        }

        "support query with empty where" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            new TraitAttribute1("1").id
                        }
                    def expected = List(byId[TraitAttribute1](entityId).get)
                    step {
                        select[TraitAttribute1].where() === expected
                        query {
                            (e: TraitAttribute1) => where() select (e)
                        } === expected
                    }
                })
        }

        "support dynamic queries" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    def testQuery(asc: Boolean) =
                        dynamicQuery {
                            (e: ActivateTestEntity) =>
                                where() select (e.intValue) orderBy {
                                    if (asc)
                                        e.intValue asc
                                    else
                                        e.intValue desc
                                }
                        }
                    step {
                        newFullActivateTestEntity
                    }
                    step {
                        testQuery(false) === List(fullIntValue, emptyIntValue)
                        testQuery(true) === List(emptyIntValue, fullIntValue)
                    }
                })
        }

        "support the in operator" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                        newFullActivateTestEntity
                    }
                    step {
                        def test(intValue: Int) = {
                            val result =
                                query {
                                    (e: ActivateTestEntity) => where(e.intValue.in(List(intValue))) select (e)
                                }.head
                            result === select[ActivateTestEntity].where(_.intValue :== intValue).head
                        }
                        test(fullIntValue)
                        test(emptyIntValue)
                    }
                    step {
                        val result = select[ActivateTestEntity].where(_.intValue in List(fullIntValue, emptyIntValue)).toSet
                        result === all[ActivateTestEntity].toSet
                    }
                })
        }

        "support the notIn operator" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                        newFullActivateTestEntity
                    }
                    step {
                        def test(intValue: Int) = {
                            val result =
                                query {
                                    (e: ActivateTestEntity) => where(e.intValue.notIn(List(intValue))) select (e)
                                }.toSet
                            result === select[ActivateTestEntity].where(_.intValue :!= intValue).toSet
                        }
                        test(fullIntValue)
                        test(emptyIntValue)
                    }
                    step {
                        val result = select[ActivateTestEntity].where(_.intValue notIn List(fullIntValue, emptyIntValue))
                        result.isEmpty must beTrue
                    }
                })
        }

        "not return StorageVersion instances for Entity queries" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newFullActivateTestEntity
                    }
                    step {
                        def test(list: List[BaseEntity]) =
                            list.find(_.isInstanceOf[StorageVersion]) must beEmpty
                        test(all[BaseEntity])
                        test(select[BaseEntity].where(_.id isNotNull))
                        test(query { (e: BaseEntity) => where(e.id isNotNull) select (e) })
                    }
                })
        }

        "custom value criteria" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        fullCaseClassEntityValue
                    }
                    step {
                        val entity = fullCaseClassEntityValue
                        val selected = select[CaseClassEntity].where(_.customEncodedEntityValue :== entity.customEncodedEntityValue)
                        selected.onlyOne === entity
                    }
                })
        }

        "transient value" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                    }
                    step {
                        select[ActivateTestEntity].where(_.transientInt :== 1) must throwA[UnsupportedOperationException]
                    }
                })
        }

        "order by a select value" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newFullActivateTestEntity
                        newFullActivateTestEntity
                    }
                    step {
                        query {
                            (e: ActivateTestEntity) =>
                                where() select (e.intValue, e.stringValue) orderBy (e.intValue)
                        }
                    }
                })
        }
    }
}
