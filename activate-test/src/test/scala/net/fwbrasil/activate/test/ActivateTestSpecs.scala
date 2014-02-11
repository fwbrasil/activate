package net.fwbrasil.activate.test

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.RichList._
import scala.concurrent.Future
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.polyglotContext

class ActivateTestSpecs extends net.fwbrasil.activate.ActivateTest with ActivateTest {
    
    override def contexts = super.contexts.filter(_ != polyglotContext)
    override def executors(ctx: ActivateTestContext) = List(OneTransaction(ctx))

    "ActivateTest" >> {
        "transactionRollbackStrategy" should {
            "keep entities created previously" in {
                activateTest((step: StepExecutor) => {
                    import step.ctx._
                    transactional {
                        newEmptyActivateTestEntity
                    }
                    activateTest(transactionRollbackStrategy) {
                        all[ActivateTestEntity].size === 1
                    }
                    activateTestAsync(transactionRollbackStrategy) {
                        all[ActivateTestEntity].size === 1
                    }
                    activateTestAsyncChain(transactionRollbackStrategy) { implicit ctx =>
                        asyncAll[ActivateTestEntity].map(_.size === 1)(ctx)
                    }
                })
            }
            "clean the test modifications in the end" in {
                activateTest((step: StepExecutor) => {
                    import step.ctx._
                    transactional {
                        newEmptyActivateTestEntity
                    }
                    activateTest(transactionRollbackStrategy) {
                        all[ActivateTestEntity].onlyOne.intValue = fullIntValue
                        newEmptyActivateTestEntity
                    }
                    activateTestAsync(transactionRollbackStrategy) {
                        all[ActivateTestEntity].onlyOne.intValue = fullIntValue
                        newEmptyActivateTestEntity
                    }
                    activateTestAsyncChain(transactionRollbackStrategy) { implicit ctx =>
                        asyncAll[ActivateTestEntity]
                            .map(_.onlyOne.intValue = fullIntValue)(ctx)
                            .map(_ => newEmptyActivateTestEntity)(ctx)
                    }
                    transactional {
                        all[ActivateTestEntity].map(_.intValue) === List(emptyIntValue)
                    }
                })
            }
        }

        def testDestructiveStrategy(strategy: ActivateTestStrategy) = {
            strategy.getClass.getSimpleName should {
                "not keep entities created previously" in {
                    activateTest((step: StepExecutor) => {
                        import step.ctx._
                        transactional {
                            newEmptyActivateTestEntity
                        }
                        activateTest(strategy) {
                            all[ActivateTestEntity].size === 0
                        }
                        activateTestAsync(strategy) {
                            all[ActivateTestEntity].size === 0
                        }
                        activateTestAsyncChain(strategy) { implicit ctx =>
                            asyncAll[ActivateTestEntity].map(_.size === 0)(ctx)
                        }
                    })
                }
                "not clean the test modifications in the end" in {
                    activateTest((step: StepExecutor) => {
                        import step.ctx._
                        def check =
                            transactional {
                                all[ActivateTestEntity].size === 1
                            }
                        activateTest(strategy) {
                            newEmptyActivateTestEntity
                        }
                        check
                        activateTestAsync(strategy) {
                            newEmptyActivateTestEntity
                        }
                        check
                        activateTestAsyncChain(strategy) { implicit ctx =>
                            Future(newEmptyActivateTestEntity)(ctx)
                        }
                        check
                    })
                }
            }
        }
        testDestructiveStrategy(recreateDatabaseStrategy)
        testDestructiveStrategy(cleanDatabaseStrategy)
    }

}