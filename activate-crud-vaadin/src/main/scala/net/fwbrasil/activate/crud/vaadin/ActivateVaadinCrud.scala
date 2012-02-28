package net.fwbrasil.activate.crud.vaadin

import java.lang.reflect.Constructor

import com.vaadin.data.Item
import com.vaadin.event.ItemClickEvent
import com.vaadin.terminal.Sizeable
import com.vaadin.ui._
import java.lang.reflect.Constructor
import net.fwbrasil.activate.crud.vaadin.util.VaadinConverters._
import net.fwbrasil.activate.crud.vaadin._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.query.OrderByCriteria
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.entity.EntityValue

class ActivateVaadinCrud[E <: Entity](implicit context: ActivateContext, m: Manifest[E]) extends Window {

	implicit val transaction = new Transaction

	import context._

	def orderByCriterias: List[(E) => OrderByCriteria[_]] = List()

	super.setHeight(80, Sizeable.UNITS_PERCENTAGE)
	super.setWidth(80, Sizeable.UNITS_PERCENTAGE)
	super.setSizeFull()

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
			super.showNotification("Add, discard or delete.",
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
		new Button("Save modifications",
			doWithFormUnmodified {
				deleteUnsedEntity
				transaction.commit
				setFormNewDataSource
				super.showNotification("Modifications saved.",
					Window.Notification.TYPE_HUMANIZED_MESSAGE);
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
			if (addUpdateButton.getCaption == "Add")
				super.showNotification("Entity added to list.",
					Window.Notification.TYPE_TRAY_NOTIFICATION);
			else
				super.showNotification("Entity updated.",
					Window.Notification.TYPE_TRAY_NOTIFICATION);
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
			super.showNotification("Entity deleted.",
				Window.Notification.TYPE_TRAY_NOTIFICATION);
		})

	addComponent(
		saveButton >
			form >
			(newButton | deleteButton | discardButton | addUpdateButton) >
			(table dim (35 per, 80 per)))

	def transactionalNewEmptyEntity =
		transactional(transaction) {
			newEmptyEntity
		}

	def entityClass =
		erasureOf[E]

	def tvalFunctionOption(clazz: Class[_]) =
		EntityValue.tvalFunctionOption(clazz)

	def typeParametersTval(constructor: Constructor[E]) =
		for (typeParameter <- constructor.getParameterTypes)
			yield tvalFunctionOption(typeParameter)

	val entityConstructors =
		entityClass.getConstructors.toList.asInstanceOf[List[Constructor[E]]]

	val constructorsAndParameters =
		for (constructor <- entityConstructors)
			yield (constructor, typeParametersTval(constructor))

	val validConstructorsAndParameters =
		constructorsAndParameters.remove(_._2.find(_.isEmpty).nonEmpty).sortBy(_._2.size)

	val entityConstructorAndParametersOption =
		validConstructorsAndParameters.headOption

	def newEmptyEntity: E = {
		val option = entityConstructorAndParametersOption
		val (constructor, paramsTval) = option.getOrElse(throw new IllegalStateException("Can't find a valid constructor."))
		val params =
			for (tvalOption <- paramsTval)
				yield (tvalOption.get)(None).emptyValue.asInstanceOf[Object]
		constructor.newInstance(params: _*)
	}

	setFormNewDataSource
}