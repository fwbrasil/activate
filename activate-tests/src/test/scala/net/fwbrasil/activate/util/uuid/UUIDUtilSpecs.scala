package net.fwbrasil.activate.util.uuid

import net.fwbrasil.radon.dsl.actor._
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ProfillingUtil

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

		//		"generate unique hascodes from UUIDs" in ProfillingUtil.profile("generate") {
		//			new ActorDsl with ManyActors with OneActorPerThread {
		//				override lazy val actorsPoolSize = 200
		//				val loops = 20
		//				val ids =
		//					(for (i <- 0 until loops) yield ProfillingUtil.profile("group: " + i) {
		//						val ids = inParallelActors {
		//							UUIDUtil.generateUUID.hashCode
		//						}
		//						ids.toSet.size must beEqualTo(actorsPoolSize)
		//						ids
		//					}).flatten
		//
		//				inMainActor {
		//					ids.toSet.size must beEqualTo(actorsPoolSize * loops)
		//				}
		//			} must not beNull
		//		}

	}
}