package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.OptimisticOfflineLocking
import net.fwbrasil.activate.mysqlContext
import scala.collection.immutable.HashMap
import net.fwbrasil.activate.asyncMysqlContext

@RunWith(classOf[JUnitRunner])
class EntitySpecs extends ActivateTest {

    "Entity" should {

        "be in live cache" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                    }
                    step {
                        for (entity <- all[ActivateTestEntity])
                            entity.isInLiveCache must beTrue
                    }
                })
        }

        "return vars fields" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                    }
                    step {
                        for (entity <- all[ActivateTestEntity]) {
                            entity.vars.filter(_.name != OptimisticOfflineLocking.versionVarName).toSet.size must beEqualTo(36)
                        }
                    }
                })
        }

        "return var named" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        (newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
                    }
                    step {
                        for (entity <- all[ActivateTestEntity]) {
                            entity.intValue must beEqualTo(entity.varNamed("intValue").unary_!.asInstanceOf[Any])
                            entity.longValue must beEqualTo(entity.varNamed("longValue").unary_!.asInstanceOf[Any])
                            entity.booleanValue must beEqualTo(entity.varNamed("booleanValue").unary_!.asInstanceOf[Any])
                            entity.charValue must beEqualTo(entity.varNamed("charValue").unary_!.asInstanceOf[Any])
                            entity.stringValue must beEqualTo(entity.varNamed("stringValue").unary_!.asInstanceOf[Any])
                            entity.floatValue must beEqualTo(entity.varNamed("floatValue").unary_!.asInstanceOf[Any])
                            entity.doubleValue must beEqualTo(entity.varNamed("doubleValue").unary_!.asInstanceOf[Any])
                            entity.bigDecimalValue must beEqualTo(entity.varNamed("bigDecimalValue").unary_!.asInstanceOf[Any])
                            entity.dateValue must beEqualTo(entity.varNamed("dateValue").unary_!.asInstanceOf[Any])
                            entity.calendarValue must beEqualTo(entity.varNamed("calendarValue").unary_!.asInstanceOf[Any])
                            entity.entityValue must beEqualTo(entity.varNamed("entityValue").unary_!.asInstanceOf[Any])
                            entity.enumerationValue must beEqualTo(entity.varNamed("enumerationValue").unary_!.asInstanceOf[Any])
                        }
                    }
                })
        }

        "handle case class copy" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            CaseClassEntity(fullStringValue, fullEntityValue, fullEntityWithoutAttributeValue).id
                        }
                    val copyId =
                        step {
                            val entity = byId[CaseClassEntity](entityId).get
                            entity.copy(entityValue = emptyEntityValue).id
                        }
                    step {
                        entityId must not be equalTo(copyId)
                        val entity = byId[CaseClassEntity](entityId).get
                        val copy = byId[CaseClassEntity](copyId).get
                        entity must not be equalTo(copy)
                        entity.entityValue must be equalTo (fullEntityValue)
                        copy.entityValue must be equalTo (emptyEntityValue)
                    }
                    step {
                        all[CaseClassEntity].map(_.id).toSet must be equalTo (Set(entityId, copyId))
                    }
                })
        }

        "handle references" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val (id1, id2, id3) =
                        step {
                            val e1 = newEmptyActivateTestEntity
                            val e2 = newEmptyActivateTestEntity
                            val e3 = newEmptyActivateTestEntity
                            e1.entityValue = e2
                            e2.entityValue = e3
                            (e1.id, e2.id, e3.id)
                        }
                    def metadata[E <: Entity: Manifest] =
                        EntityHelper.getEntityMetadata(manifest[E].runtimeClass)
                    def entity(id: String) =
                        byId[ActivateTestEntity](id).get
                    def verifyReferences(id: String, map: Map[EntityMetadata, List[Entity]]) =
                        (HashMap() ++ entity(id).references) ===
                            (HashMap() ++ map)
                    step {
                        verifyReferences(id3,
                            Map(metadata[ActivateTestEntity] -> List(entity(id2)),
                                metadata[CaseClassEntity] -> List(),
                                metadata[X[_]] -> List()))
                        verifyReferences(id2,
                            Map(metadata[CaseClassEntity] -> List(),
                                metadata[ActivateTestEntity] -> List(entity(id1)),
                                metadata[X[_]] -> List()))
                        verifyReferences(id1,
                            Map(metadata[CaseClassEntity] -> List(),
                                metadata[ActivateTestEntity] -> List(),
                                metadata[X[_]] -> List()))
                    }
                    step {
                        entity(id1).canDelete === true
                        entity(id2).canDelete === false
                        entity(id3).canDelete === false
                    }
                    step {
                        entity(id2).deleteIfHasntReferences must throwA[CannotDeleteEntity]
                        entity(id3).deleteIfHasntReferences must throwA[CannotDeleteEntity]
                    }
                    step {
                        entity(id3).deleteCascade
                        entity(id3).isDeleted === true
                        entity(id2).isDeleted === true
                        entity(id1).isDeleted === true
                    }
                    step {
                        all[ActivateTestEntity] must beEmpty
                    }
                })
        }

        "perform cascade delete with circular reference" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx != mysqlContext && step.ctx != asyncMysqlContext) {
                        step {
                            val entity = newEmptyActivateTestEntity
                            entity.entityValue = entity
                        }
                        step {
                            all[ActivateTestEntity].head.deleteCascade
                        }
                        step {
                            all[ActivateTestEntity] must beEmpty
                        }
                    }
                })
        }

        "allow delete if there is only one reference and it is from self" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx != mysqlContext && step.ctx != asyncMysqlContext) {
                        step {
                            val entity = newEmptyActivateTestEntity
                            entity.entityValue = entity
                        }
                        step {
                            all[ActivateTestEntity].head.canDelete must beTrue
                        }
                        step {
                            all[ActivateTestEntity].head.delete
                        }
                        step {
                            all[ActivateTestEntity] must beEmpty
                        }
                    }
                })
        }

        "do not return in queries entities deleted in the current transaction" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        byId[ActivateTestEntity](entityId).get.delete
                        select[ActivateTestEntity].where(_.intValue :== emptyIntValue) must beEmpty
                    }
                })
        }

        "return a field original value" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (!step.isInstanceOf[OneTransaction]) {
                        step {
                            newEmptyActivateTestEntity
                        }
                        step {
                            val entity = all[ActivateTestEntity].onlyOne
                            entity.intValue === emptyIntValue
                            entity.intValue = fullIntValue
                            entity.intValue === fullIntValue
                            entity.originalValue(_.intValue) === emptyIntValue
                        }
                    }
                })
        }

    }

}