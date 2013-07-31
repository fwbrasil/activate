package net.fwbrasil.activate

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.entity.EntityHelper

@RunWith(classOf[JUnitRunner])
class CRUDSpecs extends ActivateTest {

    "Activate perssitence framework" should {
        "support CRUD" in {
            "create and retreive" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val (fullId, emptyId) =
                            step {
                                (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                            }
                        step {
                            val emptyEntity = byId[ActivateTestEntity](emptyId).get
                            validateEmptyTestEntity(entity = emptyEntity)
                            val fullEntity = byId[ActivateTestEntity](fullId).get
                            validateFullTestEntity(entity = fullEntity)
                        }
                    })
            }

            "create, update and retreive" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val (fullId, emptyId) = step {
                            (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                        }
                        step {
                            val emptyEntity = byId[ActivateTestEntity](emptyId).get
                            setFullEntity(emptyEntity)

                            val fullEntity = byId[ActivateTestEntity](fullId).get
                            setEmptyEntity(fullEntity)
                        }
                        step {
                            val fullEntity = byId[ActivateTestEntity](fullId).get
                            validateEmptyTestEntity(entity = fullEntity)
                            val emptyEntity = byId[ActivateTestEntity](emptyId).get
                            validateFullTestEntity(entity = emptyEntity)
                        }
                    })
            }

            "create, update, retreive and delete" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val (fullId, emptyId) = step {
                            (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                        }
                        step {
                            val emptyEntity = byId[ActivateTestEntity](emptyId).get
                            setFullEntity(emptyEntity)

                            val fullEntity = byId[ActivateTestEntity](fullId).get
                            setEmptyEntity(fullEntity)
                        }
                        step {
                            val fullEntity = byId[ActivateTestEntity](fullId).get
                            validateEmptyTestEntity(entity = fullEntity)
                            val emptyEntity = byId[ActivateTestEntity](emptyId).get
                            validateFullTestEntity(entity = emptyEntity)
                        }
                        step {
                            byId[ActivateTestEntity](fullId).get.delete
                            byId[ActivateTestEntity](emptyId).get.delete
                        }
                        step {
                            if (step.isInstanceOf[OneTransaction]) {
                                byId[ActivateTestEntity](fullId).get.isDeleted must beTrue
                                byId[ActivateTestEntity](emptyId).get.isDeleted must beTrue
                            } else {
                                byId[ActivateTestEntity](fullId) must beNone
                                byId[ActivateTestEntity](emptyId) must beNone
                            }
                        }
                    })
            }
            "create, retreive and delete entity without attribute" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                (new EntityWithoutAttribute).id
                            }
                        step {
                            byId[EntityWithoutAttribute](entityId).get.delete
                        }
                        step {
                            if (step.isInstanceOf[OneTransaction])
                            	byId[EntityWithoutAttribute](entityId).get.isDeleted must beTrue
                            else
                                byId[EntityWithoutAttribute](entityId) must beNone
                        }
                    })
            }

            "create, retreive, modify and delete entity with uninitialized attribute" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                (new EntityWithUninitializedValue).id
                            }
                        step {
                            byId[EntityWithUninitializedValue](entityId).get.uninitializedValue = fullStringValue
                        }
                        step {
                            byId[EntityWithUninitializedValue](entityId).get.uninitializedValue mustEqual fullStringValue
                        }
                        step {
                            byId[EntityWithUninitializedValue](entityId).get.delete
                        }
                        step {
                            if (step.isInstanceOf[OneTransaction])
                            	byId[EntityWithUninitializedValue](entityId).get.isDeleted must beTrue
                            else
                                byId[EntityWithUninitializedValue](entityId) must beNone
                        }
                    })
            }

            "create, retreive and delete case class entity" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                (new CaseClassEntity(fullStringValue, fullEntityValue, fullEntityWithoutAttributeValue)).id
                            }
                        step {
                            byId[CaseClassEntity](entityId).get.delete
                        }
                        step {
                            if (step.isInstanceOf[OneTransaction])
                            	byId[CaseClassEntity](entityId).get.isDeleted must beTrue
                            else
                                byId[CaseClassEntity](entityId) must beNone
                        }
                    })
            }

        }
    }

}