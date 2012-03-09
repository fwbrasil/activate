package net.fwbrasil.activate.crud.vaadin.example

import net.fwbrasil.activate.crud.vaadin.ActivateVaadinCrud
import net.fwbrasil.activate.storage.memory.MemoryStorage
import net.fwbrasil.activate.entity.Entity
import java.util.Date
import net.fwbrasil.activate.ActivateContext
import com.vaadin.Application
import net.fwbrasil.thor._
import net.fwbrasil.thor.thorContext._
import com.vaadin.data.util.BeanItem
import com.vaadin.ui.Button
import com.vaadin.data.Property
import com.vaadin.ui.TextField
import com.vaadin.ui.Button.ClickListener
import com.sun.tools.hat.internal.model.Root
import com.vaadin.ui.Window
import com.vaadin.ui.Label
import scala.reflect.BeanProperty
import net.fwbrasil.activate.crud.vaadin._
import net.fwbrasil.thor._

class Monitor(var dono: String, private var ligado: Boolean) extends Entity {
	def desliga =
		ligado = false
	private[example] def liga =
		ligado = true
}

object Monitor {
	//	def desligaTodosMonitores =
	//		all[Monitor].foreach(_.ligado = false)
	//	def monitoresDaDono(nome: String) =
	//		allWhere[Monitor](_.dono :== nome)
	def a = println("a")
}

class Main extends Application {
	def init = {
		reinitializeContext
		super.setTheme("runo")
		super.addWindow(new Window {
			super.addComponent(new VaadinModuleInterface(new ThorInterface))
		})
	}
}

