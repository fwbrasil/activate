package net.fwbrasil.activate.crud.vaadin.example

import com.vaadin.Application
import com.vaadin.ui._
import com.vaadin.event.ItemClickEvent
import com.vaadin.data.Item
import net.fwbrasil.thor.thorContext._
import net.fwbrasil.activate.crud.vaadin._
import java.util.Date

class Pessoa extends Entity {
	var nome: String = _
	var sobrenome: String = _
	var nomeMae: String = _
}

class Main extends Application {
	def init = {

		implicit val transaction = new Transaction

		val mainWindow = new Window("hello")

		val form = new Form();
		val pessoa =
			transactional(transaction) {
//				for(i <- 1 to 10000)
//					(new Pessoa).nome = i.toString
				new Pessoa
			}

		form.setItemDataSource(new EntityItem(pessoa)) 
		mainWindow.addComponent(form)

		
		form.setFieldFactory(new BaseFieldFactory{
			override def createField(item: Item, propertyId: Object, uiContext: Component) = {
				val field = super.createField(item, propertyId, uiContext)
				if(field.isInstanceOf[TextField])
					field.asInstanceOf[TextField].setNullRepresentation("")
				field
			}
		})
		
		val table = new Table("Teste", new EntityContainer[Pessoa])
		table.setEditable(true)
		table.setSelectable(true)
		table.setImmediate(true)
		val save = new Button("Save",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) {
					form.commit
					transaction.commit
				}
			})
		mainWindow.addComponent(save);
		
		val update = new Button("Update",
			new Button.ClickListener() {
				def buttonClick(event: Button#ClickEvent) = transactional(transaction) {
					val entity = form.getItemDataSource().asInstanceOf[EntityItem[Pessoa]].entity
					entity.nome += entity.nome + "a"
				}
			})
		mainWindow.addComponent(update);
		
		
		table.addListener(new ItemClickEvent.ItemClickListener {
			def itemClick(event: ItemClickEvent) = {
				val item = event.getItem
				form.setItemDataSource(item)
			}
		})
		mainWindow.addComponent(table);

//		var runningFlag = true
//		
//		val thread = new Thread {
//			override def run =
//				while (runningFlag) {
//					transactional(transaction) {
//						println("thread")
//						pessoa.other += 1
//						Thread.sleep(1000)
//					}
//				}
//		}
//
//		thread.start
//		
//		mainWindow.addListener(new Window.CloseListener {
//			def windowClose(event: Window#CloseEvent) = {
//				runningFlag = false
//			}
//		})

		setMainWindow(mainWindow)
	}
}