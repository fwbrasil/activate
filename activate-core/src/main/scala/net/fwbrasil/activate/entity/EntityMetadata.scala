package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.Reflection
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import net.fwbrasil.sReflection.SReflection._

class EntityPropertyMetadata(
		val entityMetadata: EntityMetadata,
		val varField: Field,
		entityMethods: List[Method],
		entityClass: Class[Entity],
		varTypes: Map[String, Class[_]]) {
	val javaName = varField.getName
	val originalName = javaName.split('$').last
	val name =
		Option(varField.getAnnotation(classOf[Alias]))
			.map(_.value)
			.getOrElse(originalName)
	val getter = entityMethods.find(_.getName == javaName).get
	val setter = entityMethods.find(_.getName == javaName + "_$eq").getOrElse(null)
	val genericParameter = {
		getter.getGenericReturnType match {
			case typ: ParameterizedType =>
				val initial = typ.getActualTypeArguments.headOption.map(_.asInstanceOf[Class[_]]).getOrElse(classOf[Object])
				if (initial == classOf[Object]) {
					val fields = entityMetadata.sClass.sFields
					val fieldOption = fields.find(_.name == originalName)
					val res = fieldOption.flatMap(_.genericParameters.headOption)
					res.getOrElse(classOf[Object])
				} else
					initial
			case other =>
				classOf[Object]
		}
	}
	val propertyType = {
		val typ = getter.getReturnType
		if (typ == classOf[Option[_]])
			genericParameter
		else
			typ
	}
	require(propertyType != null)
	if (propertyType == classOf[Enumeration#Value])
		throw new IllegalArgumentException("To use enumerations with activate you must sublcass Val. " +
			"Instead of \"type MyEnum = Value\", use " +
			"\"case class MyEnum(name: String) extends Val(name)\"")
	val isMutable =
		setter != null
	val isTransient =
		Modifier.isTransient(varField.getModifiers)
	val isOption =
		getter.getReturnType == classOf[Option[_]]
	val tval =
		EntityValue.tvalFunctionOption[Any](propertyType, genericParameter)
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
	val invariantMethods =
		allMethods.filter((method: Method) => method.getReturnType == classOf[Invariant] && method.getName != "invariant")
	val varFields =
		allFields.filter(
			(field: Field) =>
				classOf[Var[_]] == field.getType
					&& field.getName != "net$fwbrasil$activate$entity$Entity$$_baseVar")
	val idField =
		allFields.find(_.getName == "id").get
	def isEntityProperty(varField: Field) =
		varField.getName.split('$').last != "_baseVar" && varField.getName != "id" && allMethods.find(_.getName == varField.getName).nonEmpty
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
	lazy val sClass = toSClass(entityClass)
	override def toString = "Entity metadata for " + name
}