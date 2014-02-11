package net.fwbrasil.activate.statement.mass

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.migration.StorageVersion
import net.fwbrasil.activate.polyglotContext

@RunWith(classOf[JUnitRunner])
class MassDeleteSpecs extends ActivateTest {

    "Update framework" should {
        "delete entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                    }
                    step {
                        step.ctx.delete {
                            (entity: ActivateTestEntity) => where()
                        }
                    }
                    step {
                        all[ActivateTestEntity].map(_.intValue) must beEmpty
                    }
                })
        }
        "delete specific entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val id =
                        step {
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        step.ctx.delete {
                            (entity: ActivateTestEntity) => where(entity.id :== id)
                        }
                    }
                    step {
                        all[ActivateTestEntity] must beEmpty
                    }
                })
        }
        "delete entities in memory" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        (1 to 10).foreach(i => newEmptyActivateTestEntity)
                    }
                    step {
                        all[ActivateTestEntity].foreach(_.toString) // Just load entities
                        step.ctx.delete {
                            (entity: ActivateTestEntity) => where(entity isNotNull)
                        }
                    }
                    step {
                        all[ActivateTestEntity].map(_.intValue) must beEmpty
                    }
                })
        }
        "delete specific entities in memory" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val ids = step {
                        (1 to 10).map(i => newEmptyActivateTestEntity)
                    }
                    step {
                        all[ActivateTestEntity].foreach(_.toString) // Just load entities
                        step.ctx.delete {
                            (entity: ActivateTestEntity) => where(entity.id :== ids.head)
                        }
                    }
                    step {
                        all[ActivateTestEntity].size must beEqualTo(9)
                    }
                })
        }
        "delete entities partially in memory" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val ids =
                        step {
                            (1 to 10).map(i => newEmptyActivateTestEntity.id)
                        }
                    step {
                        byId[ActivateTestEntity](ids.last).toString // Just load entity
                        step.ctx.delete {
                            (entity: ActivateTestEntity) => where(entity isNotNull)
                        }
                    }
                    step {
                        all[ActivateTestEntity].map(_.intValue) must beEmpty
                    }
                })
        }
        "delete specific entities partially in memory" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val ids =
                        step {
                            (1 to 10).map(i => newEmptyActivateTestEntity.id)
                        }
                    step {
                        byId[ActivateTestEntity](ids.last).toString // Just load entity
                        step.ctx.delete {
                            (entity: ActivateTestEntity) => where(entity.id :== ids.last)
                        }
                    }
                    step {
                        all[ActivateTestEntity].size must beEqualTo(9)
                    }
                })
        }
        "normalize delete statement from" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        new TraitAttribute1("a")
                        new TraitAttribute2("b")
                    }
                    step {
                        step.ctx.delete {
                            (entity: TraitAttribute) => where(entity isNotNull)
                        }
                    }
                    step {
                        all[TraitAttribute].isEmpty must beTrue
                    }
                })
        }
        "work combined with a mass update" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        new TraitAttribute1("a")
                        new TraitAttribute2("b")
                    }
                    step {
                        step.ctx.update {
                            (entity: TraitAttribute) => where(entity isNotNull) set (entity.attribute := "1")
                        }
                    }
                    step {
                        step.ctx.delete {
                            (entity: TraitAttribute) => where(entity.attribute :== "1")
                        }
                    }
                    step {
                        all[TraitAttribute].isEmpty must beTrue
                    }
                })
        }
        "ignore the StorageVersion for delete[Entity]" in {
            activateTest(
                (step: StepExecutor) => {
                    if (step.ctx != polyglotContext) {
                        import step.ctx._
                        step {
                            delete {
                                (e: BaseEntity) => where()
                            }
                        }
                        step {
                            all[StorageVersion] must not beEmpty
                        }
                        unloadIndexes
                    }
                })
        }.pendingUntilFixed("https://github.com/brettwooldridge/HikariCP/pull/35")

    }
}