package net.fwbrasil.activate.performance

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.storage.prevayler.PrevaylerStorage

class ActivateTestSubjectEntity(var string: String, var string2: String, val integ: Int)
	extends PerformanceTestSubjectEntity with Entity

class ActivatePerformanceTestSubject(val fContext: () => ActivateContext) extends PerformanceTestSubject {

	val context = fContext()
	import context._

	def createEntitiesInOneTransaction(number: Long) = transactional {
		for (i <- 0l until number)
			new ActivateTestSubjectEntity(i.toString, i.toString, i.intValue)
	}
	def findAllEntitiesInOneTransaction = transactional {
		all[ActivateTestSubjectEntity]
	}

}

class PrevaylerContext extends ActivateContext {
	val storage = new PrevaylerStorage(System.currentTimeMillis.toString)
}

class ActivatePerformanceTestSubjectPrevayler extends ActivatePerformanceTestSubject(() => new PrevaylerContext)