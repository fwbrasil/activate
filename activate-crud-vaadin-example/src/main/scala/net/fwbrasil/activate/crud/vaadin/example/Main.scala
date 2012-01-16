package net.fwbrasil.activate.crud.vaadin.example

import com.vaadin.Application
import com.vaadin.ui._
import com.vaadin.event.ItemClickEvent
import com.vaadin.data.Item
import net.fwbrasil.thor.thorContext._
import net.fwbrasil.activate.crud.vaadin._
import java.util.Date
import com.vaadin.terminal.UserError
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.radon.ref.Ref

class Pessoa extends Entity {

	def nomeNotBlank = invariant(nome notBlank)
	def nomeMaxLenght = invariant(nome maxLength (30))
	var nome: String = _
	var sobrenome: String = _
	var nomeMae: String = _

	def nomeCompleto =
		{
			nome + sobrenome
		} postCond (_.isEmpty)

	override def delete =
		preCond(nome != "flaviof") {
			super.delete
		}

}

class AAA(val string: String) extends Entity {
	def stringLenght = invariant(string length (20))
}

object Test extends App {
	transactional {
		val aaa = new AAA("a")
		val pessoa = new Pessoa
		//		pessoa.nome = null
		//		pessoa.nomeCompleto
		pessoa.delete
	}
}

class Main extends Application {
	def init = {
		transactional {
		}
		super.setTheme("runo")
		setMainWindow(new CrudPessoa)
	}
}

abstract class ActivateVaadinCrud[E <: Entity: Manifest] extends Window {

	implicit val transaction = new Transaction

	val table = new Table("List", new EntityContainer[E])
	table.setSelectable(true)
	table.setImmediate(true)

	var emptyEntityOption: Option[E] = None

	val form = new Form();
	form.setFieldFactory(new BaseFieldFactory {
		override def createField(item: Item, propertyId: Object, uiContext: Component) = {
			val field = super.createField(item, propertyId, uiContext)
			if (field.isInstanceOf[TextField])
				field.asInstanceOf[TextField].setNullRepresentation("")
			field
		}
	})
	form.setImmediate(true)
	form.setWriteThrough(false)

	def setFormDataSource(entityItem: EntityItem[E]) = {
		if (form.isModified)
			super.showNotification("This is a warning",
				"Add, discard or delete.",
				Window.Notification.TYPE_WARNING_MESSAGE);
		else
			form.setItemDataSource(entityItem)
	}
	def setFormNewDataSource = {
		val freshEntity = transactionalNewEmptyEntity
		setFormDataSource(new EntityItem(freshEntity))
	}
	setFormNewDataSource

	table.addListener(new ItemClickEvent.ItemClickListener {
		def itemClick(event: ItemClickEvent) = {
			val item = event.getItem.asInstanceOf[EntityItem[E]]
			setFormDataSource(item)
		}
	})

	val saveButton =
		new Button("Save",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					transaction.commit
				}
			})

	val newButton =
		new Button("New",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					setFormNewDataSource
				}
			})

	val addButton =
		new Button("Add",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					val item = form.getItemDataSource().asInstanceOf[EntityItem[E]]
					form.commit
					if (!table.containsId(item.entity.id))
						table.addItem(item.entity.id)
					table.refreshRowCache
				}
			})

	val discardButton =
		new Button("Discard",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					form.discard
				}
			})

	val deleteButton =
		new Button("Delete",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					val item = form.getItemDataSource().asInstanceOf[EntityItem[E]]
					val entity = item.entity
					transactional(transaction) {
						entity.delete
					}
					table.removeItem(item.entity.id)
					table.refreshRowCache
					form.discard
					setFormNewDataSource
				}
			})

	val listLayout = new HorizontalLayout
	listLayout.addComponent(table)

	val formLayout = new HorizontalLayout
	formLayout.addComponent(form)

	super.addComponent(saveButton)
	super.addComponent(newButton)

	super.addComponent(listLayout)
	super.addComponent(formLayout)
	super.addComponent(addButton)
	super.addComponent(discardButton)
	super.addComponent(deleteButton)

	def transactionalNewEmptyEntity =
		transactional(transaction) {
			newEmptyEntity
		}
	def newEmptyEntity: E
}

class CrudPessoa extends ActivateVaadinCrud[Pessoa] {
	def newEmptyEntity = new Pessoa
}