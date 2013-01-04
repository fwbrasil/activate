package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.memory.TransientMemoryStorage

@RunWith(classOf[JUnitRunner])
class NestedEntitySpecs extends ActivateTest {

	object Context extends ActivateContext {
		val storage = new TransientMemoryStorage
	}
	import Context._

	// Embedded entities:
	class Box(var contains: List[Num] = Nil) extends Entity {
		def add(n: Int) { contains = new Num(this, n) :: contains }
	}
	class Num(
		val container: Box,
		var num: Int) extends Entity

	// Embedded IDs:
	class IdBox(var containsIds: List[String] = Nil) extends Entity {
		def contains = containsIds.foldLeft(List[IdNum]())((acc, id) => byId[IdNum](id) match {
			case Some(s) => s :: acc
			case None => acc
		}).reverse
		def add(n: Int) = {
			val newNum = new IdNum(this.id, n)
			containsIds = newNum.id :: containsIds
			newNum
		}
	}

	class IdNum(
			val containerId: String,
			var num: Int) extends Entity {
		def container = byId[IdBox](containerId).get
	}

	"Nested entities" should {
		"Not break when nesting IDs" in transactional {
			val box = new IdBox
			val nums = (1 to 10).map(box.add(_))
			println(box.contains)
			box.contains === nums.reverse
		}

		"Not break when nesting entities" in transactional {
			val box = new Box
			val nums = (1 to 10).map(box.add(_))

			// These each throw a different exception:
			println(box.contains)
			box.contains === nums.reverse
		}
	}
}