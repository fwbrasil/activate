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
        "manipulate json strings" in {
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
                    val (newFullEntityId1, newEmptyEntityId1) =
                        step {
                            createOrUpdateEntityFromJson[ActivateTestEntity](fullEntityJson)
                            createOrUpdateEntityFromJson[ActivateTestEntity](emptyEntityJson)
                            def removeId(json: String, entity: Entity) =
                                json.replaceFirst(",\"id\":\"" + entity.id + "\"", "")
                                    .replaceFirst("\"id\":\"" + entity.id + "\"", "")
                            (createOrUpdateEntityFromJson[ActivateTestEntity](removeId(fullEntityJson, fullEntity)).id,
                                createOrUpdateEntityFromJson[ActivateTestEntity](removeId(emptyEntityJson, emptyEntity)).id)
                        }
                    step {
                        validateFullTestEntity(fullEntity)
                        validateEmptyTestEntity(emptyEntity)
                        validateEmptyTestEntity(byId[ActivateTestEntity](newEmptyEntityId1).get)
                        validateFullTestEntity(byId[ActivateTestEntity](newFullEntityId1).get)
                    }
                    val (newFullEntityId2, newEmptyEntityId2) =
                        step {
                            (createEntityFromJson[ActivateTestEntity](fullEntityJson).id,
                                createEntityFromJson[ActivateTestEntity](emptyEntityJson).id)
                        }
                    step {
                        validateEmptyTestEntity(byId[ActivateTestEntity](newEmptyEntityId2).get)
                        validateFullTestEntity(byId[ActivateTestEntity](newFullEntityId2).get)
                    }
                })
        }
    }

}