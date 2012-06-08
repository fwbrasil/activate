package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.Reflection
import java.lang.reflect.Field
import java.lang.reflect.Method

class EntityPropertyMetadata(
		val entityMetadata: EntityMetadata,
		val varField: Field,
		entityMethods: List[Method],
		entityClass: Class[Entity],
		varTypes: Map[String, Class[_]]) {
	val javaName = varField.getName
	val name = javaName.split('$').last
	val propertyType =
		varTypes.getOrElse(name, null)
	require(propertyType != null)
	if (propertyType == classOf[Enumeration#Value])
		throw new IllegalArgumentException("To use enumerations with activate you must sublcass Val. " +
			"Instead of \"type MyEnum = Value\", use " +
			"\"case class MyEnum(name: String) extends Val(name)\"")
	val getter = entityMethods.find(_.getName == javaName).get
	val setter = entityMethods.find(_.getName == javaName + "_$eq").getOrElse(null)
	val isMutable =
		setter != null
	val tval =
		EntityValue.tvalFunctionOption[Any](propertyType)
			.getOrElse(throw new IllegalStateException("Invalid entity property type. " + entityMetadata.name + "." + name + ": " + propertyType))
	varField.setAccessible(true)
	getter.setAccessible(true)
	override def toString = "Property: " + name
}

class EntityMetadata(
		val name: String,
		val entityClass: Class[Entity]) {
	val allFields =
		Reflection.getDeclaredFieldsIncludingSuperClasses(entityClass)
	val allMethods =
		Reflection.getDeclaredMethodsIncludingSuperClasses(entityClass)
	val varFields =
		allFields.filter((field: Field) => classOf[Var[_]] == field.getType)
	val idField =
		allFields.find(_.getName == "id").get
	def isEntityProperty(varField: Field) =
		varField.getName != "id" && allMethods.find(_.getName == varField.getName).nonEmpty
	val varTypes = {
		import scala.collection.JavaConversions._
		var clazz: Class[_] = entityClass
		var ret = Map[String, Class[_]]()
		do {
			val map = Reflection.getStatic(clazz, "varTypes").asInstanceOf[java.util.HashMap[String, Class[_]]]
			if (map != null)
				ret ++= map
			clazz = clazz.getSuperclass
		} while (clazz != null)
		ret
	}
	val propertiesMetadata =
		(for (varField <- varFields; if (isEntityProperty(varField)))
			yield new EntityPropertyMetadata(this, varField, allMethods, entityClass, varTypes)).sortBy(_.name)
	allMethods.foreach(_.setAccessible(true))
	allFields.foreach(_.setAccessible(true))
	override def toString = "Entity metadata for " + name
}