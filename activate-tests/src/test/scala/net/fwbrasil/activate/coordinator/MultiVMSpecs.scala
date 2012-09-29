package net.fwbrasil.activate.coordinator

import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.migration.Migration

object coordinatorTestContext extends ActivateContext {
	val storage = new PooledJdbcRelationalStorage {
		val jdbcDriver = "org.postgresql.Driver"
		val user = "postgres"
		val password = ""
		val url = "jdbc:postgresql://127.0.0.1/activate_test"
		val dialect = postgresqlDialect
	}
}

import coordinatorTestContext._

class CreateTables extends Migration {

	val timestamp = 1l

	def up = {
		createTableForAllEntities.ifNotExists
	}
}

class SomeEntity(var integer: Int) extends Entity

trait Person extends Entity {
	var name: String
	def nameAsUpperCase =
		name.toUpperCase
}
object Person {
	def personsWhereNameStartsWith(string: String) =
		select[Person] where (_.name like string + "%")
}
class NaturalPerson(var name: String, var motherName: String) extends Person {
	def modifyNameAndMotherName(name: String, motherName: String) = {
		this.name = name
		this.motherName = motherName
	}
}
class LegalPerson(var name: String, var director: NaturalPerson) extends Person

/*
 * Tipos de requisições
 * GET - Transação read-only (se tiver alguma alteração vai dar erro)
 * POST - Transação não read-only (pode fazer alterações)
 * 
 * **************
 * QUERIES 
 * (Faz mais sentido usar GET!)
 * **************
 * 
 * Obter todas pessoas
 * http://fwbrasil.net/myApp/person/all
 * 
 * Pesquisar pessoa por atributo
 * http://fwbrasil.net/myApp/person/allWhere?nome=Flavio
 *
 * Pesquisar pessoa por atributo aninhado
 * http://fwbrasil.net/myApp/legalPerson/allWhere?director.name=Flavio
 * 
 * Obter uma pessoa dado ID (poderia usar o allWhere)
 * http://fwbrasil.net/myApp/person/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e
 * 
 * **************
 * BEHAVIORS
 * (Faz mais sentido usar POST, a não ser que seja um método read-only)
 * **************
 * 
 * Chamar construtor
 * http://fwbrasil.net/myApp/naturalPerson/create?name=Flavio&motherName=Jandira
 * 
 * Chamar um método de instância
 * http://fwbrasil.net/myApp/person/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/nameAsUpperCase
 *
 * Chamar um método de instância (getter)
 * http://fwbrasil.net/myApp/person/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/name
 * 
 * Chamar um método de instância (setter)
 * http://fwbrasil.net/myApp/person/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/name?value=Novo nome
 * 
 * Chamar um método de instância (delete)
 * http://fwbrasil.net/myApp/person/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/delete
 * 
 * Chamar um método de instância com parâmetro
 * http://fwbrasil.net/myApp/person/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/modifyNameAndMotherName?name=Novo nome&motherName=Nova mae
 * 
 * Chamar um método de classe
 * http://fwbrasil.net/myApp/person/personsWhereNameStartsWith?string=Fla
 * 
 * Navegar em métodos (getters)
 * http://fwbrasil.net/myApp/legalPerson/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/director/name
 * 
 * Navegar em métodos (getter e método)
 * http://fwbrasil.net/myApp/legalPerson/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/director/modifyNameAndMotherName?name=Novo nome&motherName=Nova mae
 * 
 * **************
 * OBSERVACOES
 * **************
 *  
 * As chamadas que possuem o ID da entidade podem ser feitas sem colocar o tipo no caminho. Por exemplo
 * http://fwbrasil.net/myApp/person/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/name
 * Pode ser chamado como:
 * http://fwbrasil.net/myApp/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e/name
 * Essa forma possui um pequeno custo de performance para detectar o tipo baseado no ID
 *  
 *  
 *  
 * Se uma entidade possui outra entidade, será retornado somente o ID! Por exemplo:
 * http://fwbrasil.net/myApp/legalPerson/c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e
 * 
 * Vai retornar
 * { id: c7970067-a8e3-11e1-803c-0976a061ace5-10c9871e, name: Objective, director: c909019-a8e3-11e1-803c-0976a061ace5-10c9671e
 *  
 */

case class Runner(entityId: String, numOfVMs: Int, numOfThreads: Int, numOfTransactions: Int) {
	def run = {
		val tasks =
			for (i <- 0 until numOfVMs)
				yield fork(false)
		tasks.map(_.execute)
		tasks.map(_.join)
	}
	def fork(server: Boolean) = {
		val option =
			if (server)
				"-Dactivate.coordinator.server=true"
			else
				"-Dactivate.coordinator.serverHost=localhost"
		JvmFork.fork(128, 1024, Some(option)) {
			runThreads
		}
	}
	def runThreads = {
		val threads =
			for (i <- 0 until numOfThreads)
				yield new Thread {
				override def run =
					for (i <- 0 until numOfTransactions)
						transactional {
							byId[SomeEntity](entityId).get.integer += 1
						}
			}
		threads.map(_.start)
		threads.map(_.join)
	}
}

object Teste extends App {

	val numOfVMs = 2
	val numOfThreads = 2
	val numOfTransactions = 100

	val entityId =
		transactional {
			new SomeEntity(0).id
		}

	Runner(entityId, numOfVMs, numOfThreads, numOfTransactions).run

	val i = transactional {
		byId[SomeEntity](entityId).get.integer
	}
	println(i)
	require(i == numOfVMs * numOfThreads * numOfTransactions)
}