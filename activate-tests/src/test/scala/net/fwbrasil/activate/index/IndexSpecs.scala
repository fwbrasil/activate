package net.fwbrasil.activate.index

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.derbyContext
import net.fwbrasil.activate.h2Context
import net.fwbrasil.activate.hsqldbContext
import net.fwbrasil.activate.util.ThreadUtil
import scala.util.Random
import net.fwbrasil.activate.ActivateTestContext

@RunWith(classOf[JUnitRunner])
abstract class IndexSpecs extends ActivateTest {

    override def contexts = super.contexts.filter(c => c != derbyContext && c != h2Context && c != hsqldbContext)

    type I <: ActivateIndex[ActivateTestContext#ActivateTestEntity, Int]

    def indexFor(context: ActivateTestContext): I

    "Memory indexes" should {

        "return correct values" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity
                            newFullActivateTestEntity.id
                        }
                    step {
                        val indexed =
                            indexFor(step.ctx).get(fullIntValue).toSet
                        val normal =
                            query {
                                (e: ActivateTestEntity) => where(e.intValue :== fullIntValue) select (e)
                            }
                        indexed.toList === normal
                    }
                })
        }

        "be updated for new/modified/deleted entities" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                    }
                    def runIndexed =
                        indexFor(step.ctx).get(emptyIntValue).toSet
                    def runIndexedAndVerify =
                        runIndexed ===
                            query {
                                (e: ActivateTestEntity) => where(e.intValue :== emptyIntValue) select (e)
                            }.toSet
                    step {
                        runIndexedAndVerify
                    }
                    step {
                        newEmptyActivateTestEntity
                    }
                    step {
                        runIndexedAndVerify
                    }
                    step {
                        newEmptyActivateTestEntity.intValue = fullIntValue
                    }
                    step {
                        runIndexedAndVerify
                    }
                    step {
                        runIndexed.head.intValue = fullIntValue
                    }
                    step {
                        runIndexedAndVerify
                    }
                    step {
                        all[ActivateTestEntity].foreach(_.delete)
                    }
                    step {
                        runIndexedAndVerify
                    }
                })
        }

        "be consistent with concurrent reads/writes" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        transactional {
                            newFullActivateTestEntity.id
                        }
                    ThreadUtil.runWithThreads(100) {
                        val lazyList =
                            transactional {
                                val lazyList = indexFor(step.ctx).get(fullIntValue)
                                lazyList.head.longValue = Random.nextLong
                                lazyList
                            }
                        lazyList.ids === List(entityId)
                    }
                })
        }
    }

    def timeToRun(f: => Unit) = {
        val start = System.currentTimeMillis
        f
        System.currentTimeMillis - start
    }

}
