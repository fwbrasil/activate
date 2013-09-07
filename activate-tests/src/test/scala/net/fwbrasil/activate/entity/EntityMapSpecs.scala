package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.lift.EntityForm

class User(var name: String, var email: String) extends Entity {
    
    private def emailPattern =
        "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";

    protected def invariantNameMustBeNotEmpty =
        on(_.name).invariant(name.nonEmpty)
        
    protected def invariantEmailMustBeValid =
        on(_.email).invariant(email.matches(emailPattern))
}

@RunWith(classOf[JUnitRunner])
class EntityMapSpecs extends ActivateTest {

    "Lazy entities" should {
        "lazy load" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = new EntityMap[User](_.email -> "a@a.com")

                        val email: String = map(_.email)

                        email === "a@a.com"

                        val newEmail = "b@a.com"

                        map.put(_.email)(newEmail)
                        map.put(_.name)("aaaa")

                        map(_.email) === newEmail

                        map.createEntity.email === newEmail

                    }
                })
        }
    }

}