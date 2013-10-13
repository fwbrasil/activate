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

@RunWith(classOf[JUnitRunner])
class MemoryIndexSpecs extends ActivateTest {

    override def contexts = super.contexts.filter(c => c != derbyContext && c != h2Context && c != hsqldbContext)

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
                            indexActivateTestEntityByIntValue.get(fullIntValue).toSet
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
                        indexActivateTestEntityByIntValue.get(emptyIntValue).toSet
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
                        transactional {
                            val lazyList = indexActivateTestEntityByIntValue.get(fullIntValue)
                            lazyList.ids === List(entityId)
                            val entity = lazyList.head
                            entity.id === entityId
                            entity.longValue = Random.nextLong
                        }
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
