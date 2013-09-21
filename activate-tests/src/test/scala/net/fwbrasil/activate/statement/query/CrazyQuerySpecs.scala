package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.derbyContext
import net.fwbrasil.activate.h2Context
import net.fwbrasil.activate.hsqldbContext

@RunWith(classOf[JUnitRunner])
class CrazyQuerySpecs extends ActivateTest {
    
    override def contexts = super.contexts.filter(_.storage.supportsQueryJoin)

    "Crazy queries" should {
        "be consistent" in {
            "join with dirty entity" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                newFullActivateTestEntity.id
                            }
                        def entity = byId[ActivateTestEntity](entityId).get
                        step {
                            val int = 213
                            entity.entityValue.intValue = int
                            val result =
                                query {
                                    (e: ActivateTestEntity) => where(e.entityValue.intValue :== int) select (e.entityValue)
                                }
                            result === List(entity.entityValue)
                        }
                    })
            }
            "join with dirty entity and uninitialized entity" in {
                activateTest(
                    (step: StepExecutor) => {
                        if (!step.ctx.storage.isMemoryStorage && step.isInstanceOf[MultipleTransactionsWithReinitialize]) {
                            import step.ctx._
                            val entityId =
                                step {
                                    newFullActivateTestEntity.entityValue.id
                                }
                            def entity = byId[ActivateTestEntity](entityId).get
                            step {
                                val int = 213
                                entity.intValue = int
                                val result =
                                    query {
                                        (e: ActivateTestEntity) => where(e.entityValue.intValue :== int) select (e.entityValue)
                                    }
                                result === List(entity)
                            }
                        }
                    })
            }.pendingUntilFixed("Send partial modifications to db queries")
            
            "join with deleted entities" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                newFullActivateTestEntity.id
                            }
                        def entity =
                            byId[ActivateTestEntity](entityId).get
                        step {
                            val int = 132
                            entity.entityValue.intValue = int
                            entity.delete
                            val result =
                                query {
                                    (e: ActivateTestEntity) => where(e.entityValue.intValue :== int) select (e)
                                }
                            result must beEmpty
                        }
                    })
            }
            "deleted entities" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        val entityId =
                            step {
                                newFullActivateTestEntity.id
                            }
                        def entity =
                            byId[ActivateTestEntity](entityId).get
                        step {
                            entity.delete
                            val result =
                                query {
                                    (e: ActivateTestEntity) => where(e.intValue :== fullIntValue) select (e)
                                }
                            result must beEmpty
                        }
                    })
            }
            "join with entities initialized partially" in {
                activateTest(
                    (step: StepExecutor) => {
                        if (!step.ctx.storage.isMemoryStorage && step.isInstanceOf[MultipleTransactionsWithReinitialize]) {
                            import step.ctx._
                            val entityId =
                                step {
                                    newFullActivateTestEntity.entityValue.id
                                }
                            def entity =
                                byId[ActivateTestEntity](entityId).get
                            step {
                                entity.intValue // just initialize
                                val (full, empty) =
                                    query {
                                        (e: ActivateTestEntity) => where(e.entityValue.intValue :== emptyIntValue) select (e, e.entityValue)
                                    }.onlyOne
                                empty === entity
                                empty.isInitialized must beTrue
                                full.isInitialized must beFalse
                            }
                        }
                    })
            }
            "forced graph initialization by dirty entity" in {
                activateTest(
                    (step: StepExecutor) => {
                        if (!step.ctx.storage.isMemoryStorage && step.isInstanceOf[MultipleTransactionsWithReinitialize]) {
                            import step.ctx._
                            val entityId =
                                step {
                                    newFullActivateTestEntity.id
                                }
                            def entity =
                                byId[ActivateTestEntity](entityId).get
                            step {
                                val int = 321
                                entity.intValue = int
                                val (full, empty) =
                                    query {
                                        (e: ActivateTestEntity) => where(e.intValue :== int) select (e, e.entityValue)
                                    }.onlyOne
                                full === entity
                                full.isInitialized must beTrue
                                empty.isInitialized must beTrue
                            }
                        }
                    })
            }
        }
    }

}