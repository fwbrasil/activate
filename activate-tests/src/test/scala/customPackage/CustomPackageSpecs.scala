package customPackage

import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.ActivateTest

class CustomPackageEntity(val string: String) extends BaseEntity with UUID

class CustomPackageSpecs extends ActivateTest {

    "Activate perssitence framework" should {
        "support CRUD" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            (new CustomPackageEntity(fullStringValue)).id
                        }
                    step {
                        val entity = byId[CustomPackageEntity](entityId).get
                        entity.string === fullStringValue
                    }
                })
        }
    }

}