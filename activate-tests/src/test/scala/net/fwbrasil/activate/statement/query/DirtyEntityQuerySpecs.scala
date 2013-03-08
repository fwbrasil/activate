package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DirtyEntityQuerySpecs extends ActivateTest {

    "Dirty entity query" should {
        "work without join" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val string = "someString"
                    val entityId = step {
                        newEmptyActivateTestEntity.id
                    }
                    def entity = byId[ActivateTestEntity](entityId).get
                    step {
                        entity.stringValue = string
                        val entities = select[ActivateTestEntity].where(_ isNotNull)
                        (entities.map(_.stringValue)) mustEqual (List(string))
                        query {
                            (e: ActivateTestEntity) => where(e isNotNull) select (e.stringValue)
                        } mustEqual (List(string))
                    }
                })
        }
        "work with join" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx.storage.supportsQueryJoin) {
                        val string = "someString"
                        val entityId = step {
                            val entity = newEmptyActivateTestEntity
                            entity.stringValue = string
                            val traitValue = new TraitAttribute1("")
                            entity.traitValue1 = traitValue
                            traitValue.id
                        }
                        def traitValue = byId[TraitAttribute](entityId).get
                        step {
                            traitValue.attribute = string
                            all[ActivateTestEntity]
                            val entities = select[ActivateTestEntity].where(_.traitValue1.attribute :== string)
                            (entities.map(_.traitValue1.attribute)) mustEqual (List(string))
                            query {
                                (e: ActivateTestEntity) => where(e.traitValue1.attribute :== string) select (e.stringValue)
                            } mustEqual (List(string))
                        }
                    }
                })
        }
    }
}