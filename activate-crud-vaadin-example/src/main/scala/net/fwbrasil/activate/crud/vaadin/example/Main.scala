package net.fwbrasil.activate.crud.vaadin.example

import net.fwbrasil.activate.crud.vaadin.ActivateVaadinCrud
import net.fwbrasil.activate.storage.memory.MemoryStorage
import net.fwbrasil.activate.entity.Entity
import java.util.Date
import net.fwbrasil.activate.ActivateContext
import com.vaadin.Application

object mainContext extends ActivateContext {
	val storage = new MemoryStorage
	def contextName = "mainContext"
}

import mainContext._

abstract class Pessoa(var nome: String) extends Entity
class PessoaFisica(nome: String, var nomeMae: String) extends Pessoa(nome)
class PessoaJuridica(nome: String, var diretor: PessoaFisica) extends Pessoa(nome)

class Main extends Application {
	def init = {
		reinitializeContext
		super.setTheme("runo")
		setMainWindow(new CrudPessoa)

		transactional {
			val pessoa = new PessoaFisica("Fulano", "Maria")
			pessoa.nome = "Fulano2"
			println(pessoa.nome)
		}

		val q = query {
			(pessoa: Pessoa) => where(pessoa.nome :== "Teste") select (pessoa)
		}

		transactional {
			val result = q.execute
			for (pessoa <- result)
				println(pessoa.nome)
		}

		val q2 = query {
			(empresa: PessoaJuridica, diretor: PessoaFisica) => where(empresa.diretor :== diretor) select (empresa, diretor)
		}
		val q3 = query {
			(empresa: PessoaJuridica) => where(empresa.diretor.nome :== "Silva") select (empresa)
		}

		transactional {
			for (pessoa <- all[Pessoa])
				pessoa.delete
		}

		val transaction = new Transaction
		transactional(transaction) {
			new PessoaFisica("Teste", "Mae")
		}
		transaction.commit

		transactional {
			val pessoa = new PessoaFisica("Teste", "Mae")
			transactional(nested) {
				pessoa.nome = "Teste2"
			}
			println(pessoa.nome)
		}

	}
}

class CrudPessoa extends ActivateVaadinCrud[PessoaFisica] {
	def newEmptyEntity = new PessoaFisica("a", null)
	override def orderByCriterias = List(_.nome)
}