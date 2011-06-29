package com.example

import net.fwbrasil.activate.storage.appengine.AppengineStorage
import javax.servlet.http.{ HttpServlet, HttpServletRequest, HttpServletResponse }
import net.fwbrasil.activate.ActivateContext
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

object TesstContext extends ActivateContext {
	def contextName = "test"
	val storage = new AppengineStorage

}

import TesstContext._

case class TestEntity(val test: Var[String]) extends Entity

object Main {
	def main(args: Array[String]): Unit = {
		val helper = new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig())
		helper.setUp
		val e = transactional {
			TestEntity("a")
		}
		transactional {
			e.delete
		}
		helper.tearDown
	}
}

class HelloWorld extends HttpServlet {
	override def doGet(request: HttpServletRequest, response: HttpServletResponse) = {
		response.setContentType("text/plain")
		response.getWriter.println("Hello, worlddddd")

	}
}
