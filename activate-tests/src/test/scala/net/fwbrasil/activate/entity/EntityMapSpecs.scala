package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.lift.EntityForm

@RunWith(classOf[JUnitRunner])
class EntityMapSpecs extends ActivateTest {

    "Entity map" should {
        "receive initial values" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = new EntityMap[ActivateTestEntity](_.intValue -> fullIntValue)
                        map(_.intValue) === fullIntValue
                    }
                })
        }
        "update entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val entity = newFullActivateTestEntity
                        val map = new EntityMap[ActivateTestEntity](entity)
                        map.put(_.intValue)(emptyIntValue)
                        map.updateEntity(entity)
                        entity.intValue === emptyIntValue
                    }
                })
        }
        "initialize based on an entity and create an entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId = 
                        step {
                        val map = new EntityMap[ActivateTestEntity](newFullActivateTestEntity)
                        map.createEntity.id
                    }
                    step {
                        validateFullTestEntity(entity(entityId))
                    }
                })
        }
        "create entity with partial values" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId = 
                        step {
                        val map = new EntityMap[ActivateTestEntity](_.intValue -> fullIntValue)
                        		map.createEntity.id
                        }
                    step {
                        entity(entityId).intValue === fullIntValue
                    }
                })
        }
        "update entity with partial values" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId = 
                        step {
                        newEmptyActivateTestEntity.id
                    }
                    step {
                        val map = new EntityMap[ActivateTestEntity](_.intValue -> fullIntValue)
                        map.updateEntity(entity(entityId))
                    }
                    step {
                        entity(entityId).intValue === fullIntValue
                    }
                })
        }
        "modify value" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = new EntityMap[ActivateTestEntity]()
                        map.put(_.intValue)(fullIntValue)
                        map(_.intValue) === fullIntValue
                    }
                })
        }
        "modify option value" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = new EntityMap[ActivateTestEntity]()
                        map.put(_.optionValue)(fullOptionValue)
                        map(_.optionValue) === fullOptionValue
                    }
                })
        }
    }
    
    private def entity(id: String)(implicit ctx: ActivateTestContext) = {
        import ctx._
        byId[ActivateTestEntity](id).get
    }

}