package net.fwbrasil.activate.json4s

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization
import net.fwbrasil.activate.memoryContext

@RunWith(classOf[JUnitRunner])
class Json4sSpecs extends ActivateTest {

    "The Json4s support" should {
        "serialize entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val (emptyEntityId, fullEntityId) =
                        step {
                            (newEmptyActivateTestEntity.id, newFullActivateTestEntity.id)
                        }
                    def emptyEntity = byId[ActivateTestEntity](emptyEntityId).get
                    def fullEntity = byId[ActivateTestEntity](fullEntityId).get
                    val (emptyEntityJson, fullEntityJson) =
                        step {
                            (emptyEntity.toJson, fullEntity.toJson)
                        }
                    step {
                        emptyEntity.updateFromJson(fullEntityJson)
                        fullEntity.updateFromJson(emptyEntityJson)
                    }
                    step {
                        validateFullTestEntity(emptyEntity)
                        validateEmptyTestEntity(fullEntity)
                    }
                    val (newFullEntityId, newEmptyEntityId) = step {
                        (newEntityFromJson[ActivateTestEntity](fullEntityJson).id,
                            newEntityFromJson[ActivateTestEntity](emptyEntityJson).id)
                    }
                    step {
                        validateEmptyTestEntity(byId[ActivateTestEntity](newEmptyEntityId).get)
                        validateFullTestEntity(byId[ActivateTestEntity](newFullEntityId).get)
                    }
                })
        }
    }

}