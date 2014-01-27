package customPackage

import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.entity.Entity

class CustomPackageEntity(val string: String) extends Entity

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