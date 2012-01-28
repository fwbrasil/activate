package net.fwbrasil.activate.crud.vaadin.example

import com.vaadin.Application
import com.vaadin.ui._
import com.vaadin.ui.Table.HeaderClickListener
import com.vaadin.ui.Table.HeaderClickEvent
import com.vaadin.event.ItemClickEvent
import com.vaadin.data.Item
import com.vaadin.terminal.ThemeResource
import net.fwbrasil.thor.thorContext._
import net.fwbrasil.activate.crud.vaadin._
import net.fwbrasil.activate.crud.vaadin.util.VaadinConverters._
import java.util.Date
import com.vaadin.terminal.UserError
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.query.OrderByCriteria
import com.vaadin.event.FieldEvents._
import com.vaadin.event.ShortcutAction
import com.vaadin.event.ShortcutAction.KeyCode
import com.vaadin.event.ShortcutAction.ModifierKey
import com.vaadin.event.Action.Listener

class Pessoa extends Entity {
	//
	//	def nomeNotBlank = 
	//		invariant(nome notBlank)
	//	def nomeMaxLenght = 
	//		invariant(nome maxLength (30))
	var nome: String = _

	var sobrenome: String = _
	var nomeMae: String = _

	def nomeCompleto =
		{
			nome + sobrenome
		} postCond (_.nonEmpty)

	override def delete =

		preCond(nome != "flaviof") {
			super.delete
		}

}

class Main extends Application {
	def init = {
		reinitializeContext
		super.setTheme("runo")
		setMainWindow(new CrudPessoa)
	}
}

class CrudPessoa extends ActivateVaadinCrud[Pessoa](_.nome, _.nomeMae) {
	def newEmptyEntity = new Pessoa
}