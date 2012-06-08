package net.fwbrasil.activate.storage

import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.ActivateProperties
import net.fwbrasil.activate.ActivateProperties
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.mass.MassModificationStatement

trait Storage {

	def toStorage(statements: List[MassModificationStatement], assignments: List[(Var[Any], EntityValue[Any])], deletes: List[(Entity, List[(Var[Any], EntityValue[Any])])]): Unit = {}
	def fromStorage(query: Query[_]): List[List[EntityValue[_]]]

	def isMemoryStorage = false
	def supportComplexQueries = true
	def reinitialize = {

	}
	def migrate(action: StorageAction): Unit

}

trait StorageFactory {
	def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage
}

object StorageFactory {
	def fromSystemProperties(name: String)(implicit context: ActivateContext) = {
		import scala.collection.JavaConversions._
		val properties =
			new ActivateProperties(Option(context.properties), "storage")
		val factoryClassName =
			properties.getProperty(name, "factory")
		val storageFactory =
			Reflection.getCompanionObject[StorageFactory](Class.forName(factoryClassName)).get
		storageFactory.buildStorage(properties.childProperties(name))
	}
}
