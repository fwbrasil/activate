package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.memory.TransientMemoryStorage

@RunWith(classOf[JUnitRunner])
class NestedEntityListSpecs extends ActivateTest {

	"Nested entities list" should {
		"Not break in one step" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						val box = new Box
						val nums = (1 to 10).map(box.add(_))
						box.contains.toString
						box.contains === nums.reverse
					}
				})
		}
		"Not break in multiple steps" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val boxId =
						step {
							(new Box).id
						}
						def box = byId[Box](boxId).get
					val numsIds =
						step {
							(1 to 10).map(box.add(_)).map(_.id)
						}
					step {
						val nums = numsIds.map(byId[Num](_).get)
						box.contains mustEqual nums.reverse
					}
				})
		}
	}
}