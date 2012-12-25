package net.fwbrasil.activate.performance

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.storage.prevayler.PrevaylerStorage

class ActivateTestSubjectEntity(var string: String, var string2: String, var integ: Int)
	extends PerformanceTestSubjectEntity with Entity

class Pessoa extends Entity {

}

class Conta(var pessoa: Pessoa) extends Entity {

	private var saldo = BigDecimal(0)

	def atualizaSaldo(valor: BigDecimal) =
		preCondition(valor != null) {
			saldo += valor
		}

	def invariantPessoaDaContaNaoPodeSerNula =
		invariant {
			pessoa != null
		}
}

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

	def modifyAllEntitiesInOneTransaction = transactional {
		all[ActivateTestSubjectEntity].foreach(_.integ += 1)
	}

}

class PrevaylerContext extends ActivateContext {
	val storage = new PrevaylerStorage(System.currentTimeMillis.toString)
}

class ActivatePerformanceTestSubjectPrevayler extends ActivatePerformanceTestSubject(() => new PrevaylerContext)