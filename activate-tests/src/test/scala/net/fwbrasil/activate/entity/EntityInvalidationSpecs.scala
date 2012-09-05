package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.serialization.javaSerializator
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTestContext

@RunWith(classOf[JUnitRunner])
class EntityInvalidationSpecs extends ActivateTest {

	override def executors(ctx: ActivateTestContext) =
		super.executors(ctx).filter(_.isInstanceOf[MultipleTransactions])

	override def contexts =
		super.contexts.filterNot(_.storage.isMemoryStorage)

	"Entity" should {

		"Serialize using envelope" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val entity =
						step {
							newFullActivateTestEntity
						}
					step {
						entity.invalidate
					}
					val reloaded =
						step {
							val reloaded = byId[ActivateTestEntity](entity.id).get
							all[ActivateTestEntity].filter(_.id == entity.id).onlyOne mustEqual reloaded
							allWhere[ActivateTestEntity](_.id :== entity.id).onlyOne mustEqual reloaded
							all[ActivateTestEntity].contains(entity) must beFalse
							reloaded mustNotEqual entity
							reloaded
						}
					step {
						entity.entityValue must throwA[InvalidEntityException]
						reloaded.entityValue mustEqual fullEntityValue
					}
				})
		}

	}

}