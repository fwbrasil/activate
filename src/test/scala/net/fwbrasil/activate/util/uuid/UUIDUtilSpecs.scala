package net.fwbrasil.activate.util.uuid

import net.fwbrasil.radon.dsl.actor._
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.entity.Entity

@RunWith(classOf[JUnitRunner])
class UUIDUtilSpecs extends Specification {

	"UUIDUtil" should {
		"generate unique UUIDs" in {
			new ActorDsl with ManyActors with OneActorPerThread {
				override lazy val actorsPoolSize = 50
				val ids =
					inParallelActors {
						UUIDUtil.generateUUID
					}
				inMainActor {
					ids.toSet.size must beEqualTo(actorsPoolSize)
				}
			} must not beNull
		}
		
		"generate unique hascodes from UUIDs" in {
			new ActorDsl with ManyActors with OneActorPerThread {
				override lazy val actorsPoolSize = 200
				val ids =
					inParallelActors {
						UUIDUtil.generateUUID.hashCode
					}
				inMainActor {
					ids.toSet.size must beEqualTo(actorsPoolSize)
				}
			} must not beNull
		}

	}
}