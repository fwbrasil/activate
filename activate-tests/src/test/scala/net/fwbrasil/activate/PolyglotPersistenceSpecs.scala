package net.fwbrasil.activate

import net.fwbrasil.activate.util.ThreadUtil.runWithThreads
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.migration.Migration
import com.mongodb.BasicDBObject

@RunWith(classOf[JUnitRunner])
class PolyglotPersistenceSpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filter(!_.isInstanceOf[OneTransaction])

    override def contexts = super.contexts.filter(_ == polyglotContext)

    import polyglotContext._

    "Polyglot persistence" should {
        "rollback consistently all storages" in {
            "insert scenario" in
                activateTest(
                    (step: StepExecutor) => {
                        step {
                            clearStorages
                        }
                        step {
                            createEntitiesForAllStorages
                            subvertSchema
                        } must throwA[Exception]
                        step {
                            recoverSchema
                            verifyNumberOfEntitiesPerStorage(0)
                        }
                    })
            "updates scenario" in
                activateTest(
                    (step: StepExecutor) => {
                        step {
                            clearStorages
                        }
                        step {
                            createEntitiesForAllStorages
                        } 
                        step {
                            modifyStoragesEntities()
                            subvertSchema
                        } must throwA[Exception]
                        step {
                            recoverSchema
                            verifyThatEntitiesAreUnmodified
                        }
                    })
            "deletes scenario" in
                activateTest(
                    (step: StepExecutor) => {
                        step {
                            clearStorages
                        }
                        step {
                            createEntitiesForAllStorages
                        }
                        step {
                            clearStorages
                            subvertSchema
                        } must throwA[Exception]
                        step {
                            recoverSchema
                            verifyNumberOfEntitiesPerStorage(1)
                        }
                    })

            "inserts and updates scenario" in
                activateTest(
                    (step: StepExecutor) => {
                        step {
                            clearStorages
                        }
                        step {
                            createEntitiesForAllStorages
                        }
                        step {
                            modifyStoragesEntities()
                            createEntitiesForAllStorages
                            subvertSchema
                        } must throwA[Exception]
                        step {
                            recoverSchema
                            verifyNumberOfEntitiesPerStorage(1)
                            verifyThatEntitiesAreUnmodified
                        }
                    })

            "inserts, updates and delete scenario" in
                activateTest(
                    (step: StepExecutor) => {
                        step {
                            clearStorages
                        }
                        step {
                            createEntitiesForAllStorages
                        }
                        step {
                            modifyStoragesEntities()
                            createEntitiesForAllStorages
                            all[Order].foreach(_.delete)
                            subvertSchema
                        } must throwA[Exception]
                        step {
                            recoverSchema
                            verifyNumberOfEntitiesPerStorage(1)
                            verifyThatEntitiesAreUnmodified
                        }
                    })

        }

        "restrict mass statement usage" in {
            "transaction with statements of different storages" in
                activateTest(
                    (step: StepExecutor) => {
                        step {
                            clearStorages
                            createEntitiesForAllStorages
                        }
                        step {
                            update {
                                (e: Order) => where(e.key :== 1) set (e.key := 2)
                            }
                            polyglotContext.delete {
                                (e: SimpleEntity) => where(e.intValue :== 1)
                            }
                        } must throwA[IllegalStateException]
                    })

            "transaction with one statement of a storage and updates of other storages" in
                activateTest(
                    (step: StepExecutor) => {
                        step {
                            clearStorages
                            createEntitiesForAllStorages
                        }
                        step {
                            update {
                                (e: Order) => where(e.key :== 1) set (e.key := 2)
                            }
                            modifyStoragesEntities()
                        } must throwA[IllegalStateException]
                    })
        }

        "handle concurrent distributed transactions" in
            activateTest(
                (step: StepExecutor) => {
                    step {
                        clearStorages
                        createEntitiesForAllStorages
                    }
                    val numbers = 100
                    def entityA = all[ActivateTestEntity].head //mysql
                    def entityB = all[SimpleEntity].head //memory
                    step {
                        entityA.intValue = numbers
                        entityB.intValue = 0
                    }
                    step {
                        runWithThreads(numbers) {
                            transactional {
                                entityA.intValue -= 1
                                entityB.intValue += 1
                            }
                        }
                    }
                    step {
                        entityA.intValue mustEqual (0)
                        entityB.intValue mustEqual (numbers)
                    }
                })
    }

    private val migration =
        new ManualMigration {
            def up = table[ActivateTestEntity].renameTable("activate_temp")
        }

    private def subvertSchema =
        migration.execute

    private def recoverSchema =
        migration.revert

    private def clearStorages = {
        all[Order].foreach(_.delete)
        all[Num].foreach(_.delete)
        all[EntityWithUninitializedValue].foreach(_.delete)
        all[SimpleEntity].foreach(_.delete)
        all[Box].foreach(_.delete)
        all[ActivateTestEntity].foreach(_.delete)
        all[Employee].foreach(_.delete)
    }

    private def verifyNumberOfEntitiesPerStorage(num: Int) = {
        all[Order].size mustEqual (num)
        all[Num].size mustEqual (num)
        all[EntityWithUninitializedValue].size mustEqual (num)
        all[SimpleEntity].size mustEqual (num)
        all[Box].size mustEqual (num)
        all[ActivateTestEntity].size mustEqual (num)
        all[Employee].size mustEqual (num)
    }

    private def verifyThatEntitiesAreUnmodified = {
        all[Num].forall(_.num == 1) must beTrue
        all[EntityWithUninitializedValue].forall(_.uninitializedValue == null) must beTrue
        all[SimpleEntity].forall(_.intValue == 1) must beTrue
        all[Box].forall(_.contains.isEmpty) must beTrue
        all[ActivateTestEntity].forall(_.intValue == 1) must beTrue
        all[Employee].forall(_.name == "a") must beTrue
    }

    private def createEntitiesForAllStorages = {
        new Order("a") //default storage postgre
        new Num(null, 1) //derby
        new EntityWithUninitializedValue //h2
        new SimpleEntity(1) //memory
        new Box //mongo
        newEmptyActivateTestEntity.intValue = 1 //mysql
        new Employee("a", None) //prevayler
    }

    private def modifyStoragesEntities(ignoreBox: Boolean = false) = {
        all[Num].foreach(_.num = 2)
        all[EntityWithUninitializedValue].foreach(_.uninitializedValue = "b")
        all[SimpleEntity].foreach(_.intValue = 2)
        if (!ignoreBox) all[Box].foreach(_.add(2))
        all[ActivateTestEntity].foreach(_.intValue = 2)
        all[Employee].foreach(_.name = "b")
    }

}