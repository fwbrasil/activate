package net.fwbrasil.activate

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.util.ThreadUtil._
import java.util.concurrent.atomic.AtomicInteger

@RunWith(classOf[JUnitRunner])
class ConcurrencySpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        List(
            MultipleTransactions(ctx),
            MultipleTransactionsWithReinitialize(ctx),
            MultipleTransactionsWithReinitializeAndSnapshot(ctx)).filter(_.accept(ctx))

    val threads = 100

    "Activate" should {
        "be consistent" in {
            "concurrent creation" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            runWithThreads(threads) {
                                transactional {
                                    new TraitAttribute1("1")
                                }
                            }
                        }
                        step {
                            all[TraitAttribute1].size must beEqualTo(threads)
                            all[TraitAttribute1].map(_.attribute).toSet must beEqualTo(Set("1"))
                        }
                    })
            }
            "concurrent initialization" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                val entity = newEmptyActivateTestEntity
                                entity.intValue = 1
                                entity.id
                            }
                        step {
                            runWithThreads(threads) {
                                transactional {
                                    val entity = byId[ActivateTestEntity](entityId).get
                                    entity.intValue must beEqualTo(1)
                                }
                            }
                        }
                        step {
                            all[ActivateTestEntity].onlyOne.intValue must beEqualTo(1)
                        }
                    })
            }
            "concurrent modification" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                val entity = newEmptyActivateTestEntity
                                entity.intValue = 0
                                entity.id
                            }
                        step {
                            transactional(requiresNew) {
                                val entity = byId[ActivateTestEntity](entityId).get
                                entity.intValue must beEqualTo(0)
                            }
                            runWithThreads(threads) {
                                transactional {
                                    val entity = byId[ActivateTestEntity](entityId).get
                                    entity.intValue += 1
                                }
                            }
                        }
                        step {
                            all[ActivateTestEntity].onlyOne.intValue must beEqualTo(threads)
                        }
                    })
            }

            "shared concurrent modification" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val (entityId1, entityId2) =
                            step {
                                val entity1 = newEmptyActivateTestEntity
                                entity1.intValue = 1000
                                val entity2 = newEmptyActivateTestEntity
                                entity2.intValue = 0
                                (entity1.id, entity2.id)
                            }
                        step {
                            transactional(requiresNew) {
                                val entity1 = byId[ActivateTestEntity](entityId1).get
                                val entity2 = byId[ActivateTestEntity](entityId2).get
                                entity1.intValue must beEqualTo(1000)
                                entity2.intValue must beEqualTo(0)
                            }
                            runWithThreads(threads) {
                                transactional {
                                    val entity1 = byId[ActivateTestEntity](entityId1).get
                                    val entity2 = byId[ActivateTestEntity](entityId2).get
                                    entity1.intValue -= 1
                                    entity2.intValue += 1
                                }
                            }
                        }
                        step {
                            byId[ActivateTestEntity](entityId1).get.intValue mustEqual (1000 - threads)
                            byId[ActivateTestEntity](entityId2).get.intValue mustEqual (threads)
                        }
                    })
            }

            "concurrent initialization and modification" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                val entity = newEmptyActivateTestEntity
                                entity.intValue = 0
                                entity.id
                            }
                        step {
                            runWithThreads(threads) {
                                transactional {
                                    val entity = byId[ActivateTestEntity](entityId).get
                                    entity.intValue += 1
                                }
                            }
                        }
                        step {
                            all[ActivateTestEntity].onlyOne.intValue must beEqualTo(threads)
                        }
                    })
            }

        }
    }
}