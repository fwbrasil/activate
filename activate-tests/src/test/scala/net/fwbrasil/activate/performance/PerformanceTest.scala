package net.fwbrasil.activate.performance

import org.prevayler.Prevayler
import org.prevayler.PrevaylerFactory
import org.prevayler.Transaction
import java.util.Date
import scala.collection.mutable.ListBuffer
import org.prevayler.Query

object PerformanceTestMain extends App {
	new PerformanceTest(50000, 2,
		Map(
			"Prevayler" -> (() => new PrevaylerPerformanceTestSubject),
			"ActivatePrevayler" -> (() => new ActivatePerformanceTestSubjectPrevayler)))
}

class PerformanceTest(val entities: Long, threads: Int, val subjects: Map[String, Function0[PerformanceTestSubject]]) {

	require(entities % threads == 0)

	println("Entities: " + entities)
	println("Threads: " + threads)

	def registerTime[R](typ: String*)(f: => R) = {
		val start = System.currentTimeMillis
		val result = f
		val time = System.currentTimeMillis - start
		println(typ.mkString(" ") + ": " + time + " miliseconds")
		result
	}

	def runInThreads(f: Unit) = {
		(for (i <- 0 until threads) yield runInThred {
			f
		}).foreach(_.join)
	}

	def runInThred(f: => Unit) = {
		val thread = new Thread {
			override def run =
				f
		}
		thread.start
		thread
	}

	for ((name, fSubject) <- subjects) {
		val subject = registerTime(name, "boot") {
			fSubject()
		}
		val lot = entities / threads
		registerTime(name, "createEntitiesInOneTransaction") {
			runInThreads {
				subject.createEntitiesInOneTransaction(entities)
			}
		}
		registerTime(name, "findAllEntitiesInOneTransaction") {
			runInThreads {
				subject.findAllEntitiesInOneTransaction
			}
		}
		registerTime(name, "modifyAllEntitiesInOneTransaction") {
			runInThreads {
				subject.modifyAllEntitiesInOneTransaction
			}
		}
	}

}

trait PerformanceTestSubjectEntity {
	var string: String
}

trait PerformanceTestSubject {
	def createEntitiesInOneTransaction(number: Long): Unit
	def findAllEntitiesInOneTransaction: Unit
	def modifyAllEntitiesInOneTransaction: Unit
}