package net.fwbrasil.thor.business

import net.fwbrasil.thor.thorContext._

class Tyr(
		var sourceA: Source,
		var sourceB: Source) extends Entity {

	def combat = {
		val resultA = sourceA.updateAndExtract
		val tuplesA = resultA.tuples
		val templateA = resultA.template

		val resultB = sourceB.updateAndExtract
		val tuplesB = resultB.tuples
		val templateB = resultB.template

		var diff = tuplesA ++ tuplesB
		for (tupleB <- tuplesB)
			for (tupleA <- tuplesA)
				if (tupleB.equals(tupleA)) {
					diff -= tupleA
					diff -= tupleB
				}
		diff.isEmpty
	}
}