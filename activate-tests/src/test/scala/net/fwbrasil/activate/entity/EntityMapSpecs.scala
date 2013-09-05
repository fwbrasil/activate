package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext

class User(var email: String) extends Entity

@RunWith(classOf[JUnitRunner])
class EntityMapSpecs extends ActivateTest {

    "Lazy entities" should {
        "lazy load" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        val map = EntityMap[User](_.email -> "a@a.com")

                        val email: String = map(_.email)

                        email === "a@a.com"

                        val newEmail = "b@a.com"

                        map.put(_.email)(newEmail)

                        map(_.email) === newEmail
                    }
                })
        }
    }

}