package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.lift.EntityForm
import java.util.NoSuchElementException
import net.fwbrasil.activate.statement.StatementMocks

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
                        var map = new EntityMap[ActivateTestEntity](entity)
                        map = map.put(_.intValue)(emptyIntValue)
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
        "be created by the entity.toMap"  in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            val map: EntityMap[ActivateTestEntity] = newFullActivateTestEntity.toMap
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
                        var map = new EntityMap[ActivateTestEntity]()
                        map = map.put(_.intValue)(fullIntValue)
                        map(_.intValue) === fullIntValue
                    }
                })
        }
        "modify option value" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        var map = new EntityMap[ActivateTestEntity]()
                        map = map.put(_.optionValue)(fullOptionValue)
                        map(_.optionValue) === fullOptionValue
                    }
                })
        }
        "do not create invalid entities" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        var map = new EntityMap[TestValidationEntity]()
                        map = map.put(_.string1)("")
                        map.createEntity must throwA[String1MustNotBeEmpty]
                    }
                    step {
                        all[TestValidationEntity] must beEmpty
                    }
                })
        }
        "do not update entity to invalid values" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            new TestValidationEntity("a").id
                        }
                    def entity = byId[TestValidationEntity](entityId).get
                    step {
                        var map = new EntityMap[TestValidationEntity]()
                        map = map.put(_.string1)("")
                        map.updateEntity(entity) must throwA[String1MustNotBeEmpty]
                    }
                    step {
                        all[TestValidationEntity].onlyOne.string1 === "a"
                    }
                })
        }
        "throw an exception if hasn't a value and calls apply" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = new EntityMap[TestValidationEntity]()
                        map(_.string1) must throwA[NoSuchElementException]
                    }
                })
        }
        "be immutable by default" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = new EntityMap[TestValidationEntity]()
                        map.put(_.string1)("")
                        map.get(_.string1) must beNone
                    }
                })
        }
        "have a mutable version" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val s = "s"
                        val map = new MutableEntityMap[TestValidationEntity]()
                        map.put(_.string1)(s)
                        map(_.string1) === s
                    }
                })
        }
        "implement toString" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = new EntityMap[ActivateTestEntity](_.intValue -> fullIntValue)
                        map.put(_.stringValue)(fullStringValue)
                        map.toString == s"EntityMap(intValue -> $fullIntValue, stringValue -> $fullStringValue)"
                    }
                })
        }

        "support initialize using a query" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                        val map = new EntityMap[ActivateTestEntity](_.entityValue -> ActivateTestEntity.all.head)
                        map.get(_.entityValue).get === ActivateTestEntity.all.head // throws a ClassCast before the fix
                    }
                })
        }

        "create entity using the constructor" in {

            "with all parameters" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            val map = new EntityMap[TestValidationEntity](_.string1 -> "a", _.option -> Some(1))
                            map.createEntityUsingConstructor.id
                        }
                        step {
                            val entity = all[TestValidationEntity].onlyOne
                            entity.string1 === "a"
                            entity.option === Some(1)
                        }
                    })
            }

            "using default values" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            val map = new EntityMap[TestValidationEntity](_.string1 -> "a")
                            map.createEntityUsingConstructor.id
                        }
                        step {
                            val entity = all[TestValidationEntity].onlyOne
                            entity.string1 === "a"
                            entity.option === None
                        }
                    })
            }

            "can't find a constructor" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            val map = new EntityMap[TestValidationEntity]()
                            map.createEntityUsingConstructor must throwA[IllegalStateException]
                        }
                        step {
                            all[TestValidationEntity] must beEmpty
                        }
                    })
            }

            "update values not used in the constructor" in {
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            val map = new EntityMap[TestValidationEntity](_.string1 -> "a", _.string2 -> "b")
                            map.createEntityUsingConstructor.id
                        }
                        step {
                            val entity = all[TestValidationEntity].onlyOne
                            entity.string1 === "a"
                            entity.string2 === "b"
                            entity.option === None
                        }
                    })
            }

        }

        "have a proper error message for invalid arguments" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        new EntityMap[TestValidationEntity](_.option -> 213, _.string1 -> "a") must throwA[IllegalStateException]
                    }
                })

        }

    }

    private def entity(id: String)(implicit ctx: ActivateTestContext) = {
        import ctx._
        byId[ActivateTestEntity](id).get
    }

}