package net.fwbrasil.activate.util.uuid

import net.fwbrasil.activate.util.ThreadUtil._
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ProfillingUtil

@RunWith(classOf[JUnitRunner])
class UUIDUtilSpecs extends Specification {

	"UUIDUtil" should {
		"generate unique UUIDs" in {
			val ids =
				runWithThreads(10) {
					UUIDUtil.generateUUID
				}
			ids.toSet.size must beEqualTo(10)
		}

		"generate unique hascodes from UUIDs" in {
			val loops = 20
			val ids =
				(for (i <- 0 until loops) yield ProfillingUtil.profile("group: " + i) {
					val ids = runWithThreads(10) {
						UUIDUtil.generateUUID.hashCode
					}
					ids.toSet.size must beEqualTo(10)
					ids
				}).flatten

			ids.toSet.size must beEqualTo(10 * loops)
		}

	}
}