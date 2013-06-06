package net.fwbrasil.activate.jackson.json

import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest

@RunWith(classOf[JUnitRunner])
class JacksonJsonSpecs extends ActivateTest {

    "The Jackson Json support" should {
        "manipulate json strings" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    import JacksonJsonContext._

                    val (emptyEntityId, fullEntityId) =
                        step {
                            (newEmptyActivateTestEntity.id, newFullActivateTestEntity.id)
                        }
                    def emptyEntity = byId[ActivateTestEntity](emptyEntityId).get
                    def fullEntity = byId[ActivateTestEntity](fullEntityId).get
                    val (emptyEntityJson, fullEntityJson) =
                        step {
                            (emptyEntity.entityToJson, fullEntity.entityToJson)
                        }
                    step {
                        emptyEntity.entityFromJson(fullEntityJson)
                        fullEntity.entityFromJson(emptyEntityJson)
                    }
                    step {
                        validateFullTestEntity(emptyEntity)
                        validateEmptyTestEntity(fullEntity)
                    }
                    val (newFullEntityId1, newEmptyEntityId1) =
                        step {
                            (createOrUpdateEntityFromJson[ActivateTestEntity](fullEntityJson).id,
                                createOrUpdateEntityFromJson[ActivateTestEntity](emptyEntityJson).id)
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