package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.serialization.javaSerializator
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
							javaSerializator.toSerialized(newEmptyActivateTestEntity)
						}
					step {
						all[ActivateTestEntity].onlyOne must beEqualTo(javaSerializator.fromSerialized[ActivateTestEntity](serialized))
					}
				})
		}

		"Serialize not using envelope" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val serialized =
						step {
							Entity.serializeUsingEvelope = false
							val res = javaSerializator.toSerialized(newEmptyActivateTestEntity)
							Entity.serializeUsingEvelope = true
							res
						}
					step {
						val bd = all[ActivateTestEntity].onlyOne
						val ser = javaSerializator.fromSerialized[ActivateTestEntity](serialized)
						(bd == ser) must beFalse
						(bd.id == ser.id) must beTrue
					}
				})
		}

	}

}