package net.fwbrasil.activate.crud.vaadin.util

import com.vaadin.ui._
import com.vaadin.event.ItemClickEvent
import com.vaadin.terminal.Sizeable

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

	case class SizeableDecorator[S <: Sizeable](s: S) {
		def height(h: (Float, Int)) = {
			s.setHeight(h._1, h._2)
			s
		}
		def width(w: (Float, Int)) = {
			s.setWidth(w._1, w._2)
			s
		}
		def dim(h: (Float, Int), w: (Float, Int)) = {
			height(h)
			width(w)
		}
	}

	case class SizeDecorator(n: Float) {
		def px = (n, Sizeable.UNITS_PIXELS)
		def percent = (n, Sizeable.UNITS_PERCENTAGE)
		def per = percent
	}

	implicit def toSizeDecorator(n: Float) = SizeDecorator(n)
	implicit def toSizeableDecorator[S <: Sizeable](s: S) = SizeableDecorator[S](s)
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

	implicit def toMenuCommand(f: (MenuBar#MenuItem) => Unit): MenuBar.Command =
		new MenuBar.Command {
			override def menuSelected(selectedItem: MenuBar#MenuItem) = {
				f(selectedItem)
			}
		}

	implicit def toMenuCommand(f: => Unit): MenuBar.Command =
		toMenuCommand((m: MenuBar#MenuItem) => f)

}