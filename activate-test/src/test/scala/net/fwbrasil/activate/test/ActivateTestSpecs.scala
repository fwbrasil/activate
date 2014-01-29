package net.fwbrasil.activate.test

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.RichList._

class ActivateTestSpecs extends net.fwbrasil.activate.ActivateTest with ActivateTest {

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
                    })
                }
                "not clean the test modifications in the end" in {
                    activateTest((step: StepExecutor) => {
                        import step.ctx._
                        activateTest(strategy) {
                            newEmptyActivateTestEntity
                        }
                        transactional {
                            all[ActivateTestEntity].isEmpty must beTrue
                        }
                    })
                }
            }
        }
        testDestructiveStrategy(recreateDatabaseStrategy)
        testDestructiveStrategy(cleanDatabaseStrategy)
    }

}