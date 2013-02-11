package net.fwbrasil.activate.util.uuid

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class IdableSpecs extends Specification {

    "An idable" should {

        "have an id" in {
            val idable = new Idable {}
            idable.id must not be null and not be empty
        }

        "have a creationTimestamp" in {
            val idable = new Idable {}
            idable.creationTimestamp > 0 must beTrue
        }

        "have a creationDate" in {
            val idable = new Idable {}
            idable.creationDate must not be null
        }

        "have a creationTimestamp equal to creationDate" in {
            val idable = new Idable {}
            idable.creationDate.getTime must beEqualTo(idable.creationTimestamp)
        }

    }
}