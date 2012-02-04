package net.fwbrasil.activate.crud.vaadin

import com.vaadin.Application
import com.vaadin.ui._
import com.vaadin.ui.Table.HeaderClickListener
import com.vaadin.ui.Table.HeaderClickEvent
import com.vaadin.event.ItemClickEvent
import com.vaadin.data.Item
import com.vaadin.terminal.ThemeResource
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
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.ActivateContext

abstract class ActivateVaadinCrud[E <: Entity](val orderByCriterias: (E) => OrderByCriteria[_]*)(implicit context: ActivateContext, m: Manifest[E]) extends Window {

	implicit val transaction = new Transaction

	import context._

	val res = new ThemeResource("../runo/icons/16/arrow-down.png")

	val table = new Table("List", new EntityContainer[E](orderByCriterias: _*))
	table.setSelectable(true)
	table.setImmediate(true)
	for (header <- table.getColumnHeaders) {
		table.setColumnHeader(header, DefaultFieldFactory.createCaptionByPropertyId(header))
		table.setColumnIcon(header, res)
	}

	val window = new Window
	window.setClosable(true)
	window.setName("a")
	window.setDraggable(true)
	window.setCloseShortcut(KeyCode.ESCAPE)
	window.setResizable(false)
	window.setBorder(0)
	var propertyId: Option[String] = None
	val asc =
		new Button("Asc", println("asc"))
	window.addComponent(asc)
	val desc =
		new Button("Desc",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) = {}
			})
	window.addComponent(desc)

	table.addListener(new HeaderClickListener {
		def headerClick(event: HeaderClickEvent) = {
			propertyId = Option(event.getPropertyId.asInstanceOf[String])
			window.setPositionX(event.getClientX())
			window.setPositionY(event.getClientY())
			window.addListener(new BlurListener() {
				def blur(event: BlurEvent) = {
					getWindow.removeWindow(window)
				}
			})
			getWindow.addAction(new ShortcutAction("Next field", KeyCode.ESCAPE, Array[Int]()) with Listener {
				def handleAction(a: Any, b: Any) = {
					getWindow.removeWindow(window)
				}
			})
			addListener(new FocusListener() {
				def focus(event: FocusEvent) = {
					if (event.getComponent() != window)
						getWindow.removeWindow(window)
				}
			})
			if (window.getParent == null)
				getWindow.addWindow(window)
		}
	})

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

	table.addListener((event: ItemClickEvent) => {
		val item = event.getItem.asInstanceOf[EntityItem[E]]
		setFormDataSource(item)
		addUpdateButton.setCaption("Update")
	})

	val saveButton =
		new Button("Save",
			doWithFormUnmodified {
				deleteUnsedEntity
				transaction.commit
				setFormNewDataSource
			})

	val newButton =
		new Button("New",
			setFormNewDataSource)

	val addUpdateButton: Button =
		new Button("Add", {
			val item = form.getItemDataSource().asInstanceOf[EntityItem[E]]
			form.commit
			if (!table.containsId(item.entity.id))
				table.addItem(item.entity.id)
			table.refreshRowCache
			addUpdateButton.setCaption("Update")
		})

	val discardButton =
		new Button("Discard",
			form.discard)

	val deleteButton =
		new Button("Delete", {
			val item = form.getItemDataSource().asInstanceOf[EntityItem[E]]
			val entity = item.entity
			transactional(transaction) {
				entity.delete
			}
			table.removeItem(item.entity.id)
			table.refreshRowCache
			form.discard
			setFormNewDataSource
		})

	addComponent(
		(newButton | saveButton) >
			table >
			form >
			(addUpdateButton | discardButton | deleteButton))

	def transactionalNewEmptyEntity =
		transactional(transaction) {
			newEmptyEntity
		}
	def newEmptyEntity: E

	setFormNewDataSource
}