package net.fwbrasil.activate

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class CRUDSpecs extends ActivateTest {

	"Activate perssitence framework" should {
		"support CRUD" in {
			"create and retreive" in {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						val (fullId, emptyId) = step {
							(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
						}
						step {
							val emptyEntity = byId[ActivateTestEntity](emptyId).get
							validateEmptyTestEntity(entity = emptyEntity)
							val fullEntity = byId[ActivateTestEntity](fullId).get
							validateFullTestEntity(entity = fullEntity)
						}
					}
				)
			}

			"create, update and retreive" in {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						val (fullId, emptyId) = step {
							(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
						}
						step {
							val emptyEntity = byId[ActivateTestEntity](emptyId).get
							setFullEntity(emptyEntity)

							val fullEntity = byId[ActivateTestEntity](fullId).get
							setEmptyEntity(fullEntity)
						}
						step {
							val fullEntity = byId[ActivateTestEntity](fullId).get
							validateEmptyTestEntity(entity = fullEntity)
							val emptyEntity = byId[ActivateTestEntity](emptyId).get
							validateFullTestEntity(entity = emptyEntity)
						}
					}
				)
			}

			"create, update, retreive and delete" in {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						val (fullId, emptyId) = step {
							(newFullActivateTestEntity.id, newEmptyActivateTestEntity.id)
						}
						step {
							val emptyEntity = byId[ActivateTestEntity](emptyId).get
							setFullEntity(emptyEntity)

							val fullEntity = byId[ActivateTestEntity](fullId).get
							setEmptyEntity(fullEntity)
						}
						step {
							val fullEntity = byId[ActivateTestEntity](fullId).get
							validateEmptyTestEntity(entity = fullEntity)
							val emptyEntity = byId[ActivateTestEntity](emptyId).get
							validateFullTestEntity(entity = emptyEntity)
						}
						step {
							byId[ActivateTestEntity](fullId).get.delete
							byId[ActivateTestEntity](emptyId).get.delete
						}
						step {
							byId[ActivateTestEntity](fullId) must beNone
							byId[ActivateTestEntity](emptyId) must beNone
						}
					}
				)
			}

		}
	}

}