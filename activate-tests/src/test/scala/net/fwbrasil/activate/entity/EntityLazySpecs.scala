package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest

@RunWith(classOf[JUnitRunner])
class EntityLazySpecs extends ActivateTest {
	
	override def executors(ctx: ActivateTestContext) = 
		List(MultipleTransactionsWithReinitialize(ctx)) 
	
	override def contexts =
		super.contexts.filter(!_.storage.isMemoryStorage)
 
	"Lazy entities" should {
		"lazy load" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val (fullId, emptyId) = step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for(entity <- all[ActivateTestEntity]) {
							entity.isPersisted must beTrue
							entity.isInitialized must beFalse
							entity.id must not beNull;
							for(ref <- entity.vars) {
								ref.context must not beNull;
							    ref.name must not beNull;
							    ref.outerEntity must not beNull;
							}
						}
					}
				}
			)
		}
		
		"lazy load and initialize" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val (fullId, emptyId) = step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for(entity <- all[ActivateTestEntity])
							entity.isInitialized must beFalse
						for(entity <- all[ActivateTestEntity])
							entity.initialize
						for(entity <- all[ActivateTestEntity])
							entity.isInitialized must beTrue
					}
				}
			)
		}
		
		"lazy load and initialize by a var" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val (fullId, emptyId) = step {
						(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
					}
					step {
						for(entity <- all[ActivateTestEntity])
							entity.isInitialized must beFalse
						for(entity <- all[ActivateTestEntity])
							entity.vars.head.get
						for(entity <- all[ActivateTestEntity])
							entity.isInitialized must beTrue
					}
				}
			)
		}
	}

}