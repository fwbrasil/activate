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

    def indexFor(context: ActivateTestContext): ActivateIndex[_ <: ActivateTestContext#ActivateTestEntity, Int]

    "Memory indexes" should {

        "return correct values" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        indexFor(step.ctx).get(fullIntValue).ids must beEmpty
                    }
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
                    def runIndexedAndVerify = {
                        val (indexed, normal) =
                            step {
                                val indexed = runIndexed.map(_.id)
                                val normal =
                                    query {
                                        (e: ActivateTestEntity) => where(e.intValue :== emptyIntValue) select (e.id)
                                    }.toSet
                                (indexed, normal)
                            }
                        indexed === normal
                    }
                    runIndexedAndVerify
                    step {
                        newEmptyActivateTestEntity
                    }
                    runIndexedAndVerify
                    step {
                        newEmptyActivateTestEntity.intValue = fullIntValue
                    }
                    runIndexedAndVerify
                    step {
                        val entity = runIndexed.head
                        entity.intValue = fullIntValue
                    }
                    runIndexedAndVerify
                    step {
                        all[ActivateTestEntity].foreach(_.delete)
                    }
                    runIndexedAndVerify
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
