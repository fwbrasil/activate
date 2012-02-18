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
		setMainWindow(new CrudPessoaFisica)
	}
}

class CrudPessoaFisica extends ActivateVaadinCrud[PessoaFisica] {
	override def orderByCriterias = List(_.nome)
}