package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.prevaylerContext
import net.fwbrasil.activate.memoryContext

@RunWith(classOf[JUnitRunner])
class EntityTransientValueSpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filter(e =>
            e.isInstanceOf[MultipleTransactionsWithReinitialize] ||
                e.isInstanceOf[MultipleTransactionsWithReinitializeAndSnapshot])

    override def contexts = super.contexts.filter(_ != memoryContext)

    "Entity transient value" should {
        "be null after context reinitialize if it is not lazy" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx != prevaylerContext || step.isInstanceOf[MultipleTransactionsWithReinitializeAndSnapshot]) {
                        val entityId =
                            step {
                                newEmptyActivateTestEntity.id
                            }
                        step {
                            val entity = byId[ActivateTestEntity](entityId).get
                            entity.transientValue must beNull
                        }
                    }
                })
        }
        "be reinitialized after context reinitialize if it is lazy" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        val entity = byId[ActivateTestEntity](entityId).get
                        entity.transientLazyValue must not beNull
                    }
                })
        }
        "work with concurrent initialization if it is lazy" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entity =
                        transactional(newEmptyActivateTestEntity)
                    val transaction1 = new Transaction
                    val transaction2 = new Transaction
                    transactional(transaction1) {
                        entity.transientLazyValue must not beNull
                    }
                    transactional(transaction2) {
                        entity.transientLazyValue must not beNull // ERROR
                    }
                    transaction1.commit
                    transaction2.rollback
                    transactional(transaction2) {
                        entity.transientLazyValue must not beNull
                    }
                    transaction2.commit
                })
        }.pendingUntilFixed("https://github.com/fwbrasil/activate/issues/27")
    }
}