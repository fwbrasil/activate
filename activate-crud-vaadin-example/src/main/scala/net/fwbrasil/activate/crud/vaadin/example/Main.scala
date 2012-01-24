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
import net.fwbrasil.activate.query.OrderByCriteria

class Pessoa extends Entity {

	//	def nomeNotBlank = invariant(nome notBlank)
	//	def nomeMaxLenght = invariant(nome maxLength (30))
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

abstract class ActivateVaadinCrud[E <: Entity: Manifest](val orderByCriterias: (E) => OrderByCriteria[_]*) extends Window {

	implicit val transaction = new Transaction

	val table = new Table("List", new EntityContainer[E](orderByCriterias: _*))
	table.setSelectable(true)
	table.setImmediate(true)
	for (header <- table.getColumnHeaders)
		table.setColumnHeader(header, DefaultFieldFactory.createCaptionByPropertyId(header))

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

	def doWithFormUnmodified(f: => Unit) =
		if (form.isModified)
			super.showNotification("This is a warning",
				"Add, discard or delete.",
				Window.Notification.TYPE_WARNING_MESSAGE);
		else {
			f
		}

	def setFormDataSource(entityItem: EntityItem[E]) =
		doWithFormUnmodified {
			deleteUnsedEntity
			form.setItemDataSource(entityItem)
		}

	def deleteUnsedEntity =
		{
			val item = form.getItemDataSource.asInstanceOf[EntityItem[E]]
			if (item != null) {
				val oldEntity = form.getItemDataSource.asInstanceOf[EntityItem[E]].entity
				transactional(transaction) {
					if (!oldEntity.isDeleted && !table.getContainerDataSource.containsId(oldEntity.id))
						oldEntity.delete
				}
			}
		}

	def setFormNewDataSource =
		doWithFormUnmodified {
			val freshEntity = transactionalNewEmptyEntity
			setFormDataSource(new EntityItem(freshEntity))
			addUpdateButton.setCaption("Add")
		}

	table.addListener(new ItemClickEvent.ItemClickListener {
		def itemClick(event: ItemClickEvent) = {
			val item = event.getItem.asInstanceOf[EntityItem[E]]
			setFormDataSource(item)
			addUpdateButton.setCaption("Update")
		}
	})

	val saveButton =
		new Button("Save",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) =
					doWithFormUnmodified {
						deleteUnsedEntity
						transaction.commit
						setFormNewDataSource
					}
			})

	val newButton =
		new Button("New",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					setFormNewDataSource
				}
			})

	val addUpdateButton: Button =
		new Button("Add",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					val item = form.getItemDataSource().asInstanceOf[EntityItem[E]]
					form.commit
					if (!table.containsId(item.entity.id))
						table.addItem(item.entity.id)
					table.refreshRowCache
					addUpdateButton.setCaption("Update")
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
	super.addComponent(addUpdateButton)
	super.addComponent(discardButton)
	super.addComponent(deleteButton)

	def transactionalNewEmptyEntity =
		transactional(transaction) {
			newEmptyEntity
		}
	def newEmptyEntity: E

	setFormNewDataSource
}

class CrudPessoa extends ActivateVaadinCrud[Pessoa](_.nome, _.nomeMae) {
	def newEmptyEntity = new Pessoa
}