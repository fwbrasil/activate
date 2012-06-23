package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.util.RichList._

@RunWith(classOf[JUnitRunner])
class ShortEntityNameSpecs extends ActivateTest {

	"Activate" should {
		"support entities with short name" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						step.ctx.delete {
							(e: ShortNameEntity) => where(e isNotNull)
						}
					}
					val id = step {
						(new ShortNameEntity("s")).id
					}
					step {
						all[ShortNameEntity].onlyOne.id must beEqualTo(id)
					}
					step {
						val entity = byId[ShortNameEntity](id).get
						entity.string += "a"
					}
				})
		}
	}
}