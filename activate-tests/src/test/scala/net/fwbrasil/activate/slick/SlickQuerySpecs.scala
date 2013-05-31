package net.fwbrasil.activate.slick

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.memoryContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.ActivateTestContext
import scala.slick.direct.Queryable

@RunWith(classOf[JUnitRunner])
class SlickQuerySpecs extends ActivateTest {
    
    override def executors(ctx: ActivateTestContext) = 
        super.executors(ctx).filter(!_.isInstanceOf[OneTransaction])
    override def contexts = 
        super.contexts.filter(_.isInstanceOf[SlickQueryContext])

    "The Slick query support" should {
        "perform queries" in {
            activateTest(
                (step: StepExecutor) => {
                    val ctx = step.ctx.asInstanceOf[ActivateTestContext with SlickQueryContext]
                    import ctx._
                    val entityId =
                        step {
                            newFullActivateTestEntity.id
                        }
                    step {
                        val q = Queryable[ActivateTestEntity]
                        val slick = q.filter(_.stringValue == fullStringValue).toSeq.toList
                        val activate = select[ActivateTestEntity].where(_.stringValue :== fullStringValue)
                        slick === activate
                    }
                })
        }
    }

}