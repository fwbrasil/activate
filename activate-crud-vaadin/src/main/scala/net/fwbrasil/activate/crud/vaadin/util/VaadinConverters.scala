package net.fwbrasil.activate.crud.vaadin.util

import com.vaadin.ui._
import com.vaadin.event.ItemClickEvent

object VaadinConverters {

	case class LayoutDecorator(layoutA: Layout) {
		def |(layoutB: Layout) = {
			val layout = new HorizontalLayout
			layout.addComponent(layoutA)
			layout.addComponent(layoutB)
			layout
		}
		def >(layoutB: Layout) = {
			val layout = new VerticalLayout
			layout.addComponent(layoutA)
			layout.addComponent(layoutB)
			layout
		}
	}

	implicit def toLayoutDecorator(layout: Layout) = LayoutDecorator(layout)
	implicit def toLayoutDecorator(component: Component) = {
		val layout = new HorizontalLayout
		layout.addComponent(component)
		LayoutDecorator(layout)
	}

	implicit def toLayout[C <: Component](component: C): Layout = {
		val layout = new HorizontalLayout
		layout.addComponent(component)
		layout
	}

	implicit def toClickListener(f: (Button#ClickEvent) => Unit): Button.ClickListener =
		new Button.ClickListener() {
			def buttonClick(event: Button#ClickEvent) =
				f(event)
		}

	implicit def toClickListener(f: => Unit): Button.ClickListener =
		toClickListener((e: Button#ClickEvent) => f)

	implicit def toItemClickListener(f: (ItemClickEvent) => Unit): ItemClickEvent.ItemClickListener =
		new ItemClickEvent.ItemClickListener {
			def itemClick(event: ItemClickEvent) =
				f(event)
		}

	implicit def toItemClickListener(f: => Unit): ItemClickEvent.ItemClickListener =
		toItemClickListener((e: ItemClickEvent) => f)

}