package net.fwbrasil.activate.performance

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.storage.prevayler.PrevaylerStorage
import net.fwbrasil.activate.storage.memory.MemoryStorage

class ActivateTestSubjectEntity(var string: String)
	extends PerformanceTestSubjectEntity with Entity

class ActivatePerformanceTestSubject(val fContext: () => ActivateContext) extends PerformanceTestSubject {

	val context = fContext()
	import context._

	def createEntitiesInOneTransaction(number: Long) = transactional {
		for (i <- 0l until number)
			new ActivateTestSubjectEntity(i.toString)
	}
	def findAllEntitiesInOneTransaction = transactional {
		all[ActivateTestSubjectEntity]
	}

}

class PrevaylerContext extends ActivateContext {
	val storage = new MemoryStorage {
		//		override lazy val name = System.currentTimeMillis.toString
	}
	def contextName = "prevaylerContext"
}

class ActivatePerformanceTestSubjectPrevayler extends ActivatePerformanceTestSubject(() => new PrevaylerContext)