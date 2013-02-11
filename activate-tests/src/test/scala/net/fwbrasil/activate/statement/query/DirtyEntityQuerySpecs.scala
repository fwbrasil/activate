package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DirtyEntityQuerySpecs extends ActivateTest {

    "Dirty entity query" should {
        "work" in {
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
    }
}