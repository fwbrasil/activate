package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.serialization.javaSerializer
import net.fwbrasil.activate.util.RichList._

@RunWith(classOf[JUnitRunner])
class EntitySerializationSpecs extends ActivateTest {

    "Entity" should {

        "Serialize using envelope" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val serialized =
                        step {
                            javaSerializer.toSerialized(newEmptyActivateTestEntity)
                        }
                    step {
                        (all[ActivateTestEntity].onlyOne == javaSerializer.fromSerialized[ActivateTestEntity](serialized)) must beTrue
                    }
                })
        }

        "Serialize not using envelope" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val serialized =
                        step {
                            BaseEntity.serializeUsingEvelope = false
                            val res = javaSerializer.toSerialized(newEmptyActivateTestEntity)
                            BaseEntity.serializeUsingEvelope = true
                            res
                        }
                    step {
                        val bd = all[ActivateTestEntity].onlyOne
                        val ser = javaSerializer.fromSerialized[ActivateTestEntity](serialized)
                        (bd == ser) must beFalse
                        (bd.id == ser.id) must beTrue
                    }
                })
        }

    }

}