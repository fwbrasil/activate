package net.fwbrasil.activate

import net.fwbrasil.activate.util.RichList._
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityValue
import scala.util.Random
import net.fwbrasil.activate.multipleVms._

@RunWith(classOf[JUnitRunner])
class CRUDSpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext): List[StepExecutor] =
        List(
            OneTransaction(ctx),
            MultipleTransactions(ctx),
            MultipleAsyncTransactions(ctx),
            MultipleTransactionsWithReinitialize(ctx),
            MultipleTransactionsWithReinitializeAndSnapshot(ctx))

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
                            byId[ActivateTestEntity](fullId).get.isDeleted
                        } must beTrue
                        step {
                            byId[ActivateTestEntity](emptyId).get.isDeleted
                        } must beTrue
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
                            byId[EntityWithoutAttribute](entityId).get.isDeleted
                        } must beTrue
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
                            byId[EntityWithUninitializedValue](entityId).get.isDeleted
                        } must beTrue
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
                            byId[CaseClassEntity](entityId).get.isDeleted
                        } must beTrue
                    })
            }

            "Insert equal case class instances multiple times" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        if (step.ctx.storage.isMemoryStorage && step.isInstanceOf[MultipleTransactions]) {
                            step {
                                for (i <- 0 until 1000)
                                    CaseClassEntity(fullStringValue, fullEntityValue, fullEntityWithoutAttributeValue)
                            }
                            step {
                                all[CaseClassEntity].size === 1000
                            }
                        }
                    })
            }

            "custom entity value" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val i = 231
                        step {
                            fullCaseClassEntityValue.customEncodedEntityValue = new CustomEncodedEntityValue(i)
                        }
                        step {
                            fullCaseClassEntityValue.customEncodedEntityValue.i === i
                        }
                    })
            }

            "custom case objects" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                fullCaseClassEntityValue.id
                            }
                        step {
                            fullCaseClassEntityValue.userStatus === NormalUser
                        }
                        step {
                            fullCaseClassEntityValue.userStatus == NormalUser
                        }
                        step {
                            fullCaseClassEntityValue.userStatus === NormalUser
                        }
                    })
            }

            "readOny/readWrite transactions" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        transactional(readOnly) {
                            newEmptyActivateTestEntity
                        } must throwA[IllegalStateException]
                        transactional(readOnly) {
                            all[ActivateTestEntity]
                        } must beEmpty
                        val entity =
                            transactional(readWrite) {
                                newEmptyActivateTestEntity
                            }
                        transactional(readOnly) {
                            entity.intValue
                        } === emptyIntValue
                        transactional(readOnly) {
                            entity.intValue = fullIntValue
                        } must throwA[IllegalStateException]
                        transactional(readWrite) {
                            entity.intValue = fullIntValue
                            entity.intValue
                        } must beEqualTo(fullIntValue)
                    })
            }

        }
    }

}