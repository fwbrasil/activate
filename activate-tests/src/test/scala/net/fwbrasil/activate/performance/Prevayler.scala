package net.fwbrasil.activate.performance

import org.prevayler.PrevaylerFactory
import scala.collection.mutable.ListBuffer
import org.prevayler.Query
import org.prevayler.Transaction
import java.util.Date

class PrevaylerPerformanceTestSubjectEntity(var string: String, var integ: Int)
	extends PerformanceTestSubjectEntity with Serializable

class PrevaylerPerformanceTestSubjectSystem extends Serializable {
	val entities = ListBuffer[PrevaylerPerformanceTestSubjectEntity]()
}

class PrevaylerPerformanceTestSubject extends PerformanceTestSubject {

	def name = "Prevayler"

	val prevayler = {
		val factory = new PrevaylerFactory
		//		factory.configureTransactionFiltering(false)
		factory.configurePrevalentSystem(new PrevaylerPerformanceTestSubjectSystem)
		factory.configurePrevalenceDirectory(System.currentTimeMillis + "")
		factory.create
	}

	def createEntitiesInOneTransaction(number: Long) = {
		prevayler.execute(new Transaction {
			def executeOn(system: Object, date: Date) = {
				for (i <- 0l until number)
					system.asInstanceOf[PrevaylerPerformanceTestSubjectSystem].entities +=
						new PrevaylerPerformanceTestSubjectEntity(i.toString, i.intValue)
			}
		})
	}

	def findAllEntitiesInOneTransaction = {
		prevayler.execute(new Query {
			def query(system: Object, date: Date) = {
				system.asInstanceOf[PrevaylerPerformanceTestSubjectSystem].entities
			}
		})
	}

	def modifyAllEntitiesInOneTransaction = {
		prevayler.execute(new Query {
			def query(system: Object, date: Date) = {
				val entities = system.asInstanceOf[PrevaylerPerformanceTestSubjectSystem].entities
				entities.foreach(_.integ += 1)
				new Object
			}
		})
	}

}