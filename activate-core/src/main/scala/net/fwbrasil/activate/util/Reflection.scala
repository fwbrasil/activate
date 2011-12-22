package net.fwbrasil.activate.util

import scala.runtime._
import org.objenesis._
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.GenericArrayType
import org.reflections.Reflections
import scala.collection.mutable.{ Map => MutableMap }

class Reflection(val clazz: Class[_]) {
	def publicMethods = clazz.getMethods
}

object Reflection {

	val objenesis = new ObjenesisStd(false);

	def newInstance[T](clazz: Class[_]): T =
		objenesis.newInstance(clazz).asInstanceOf[T]
	
	def getDeclaredFieldsIncludingSuperClasses(concreteClass: Class[_]) = {
		var clazz = concreteClass
		var fields = List[Field]()
		do {
			fields ++= clazz.getDeclaredFields()
			clazz = clazz.getSuperclass()
		} while (clazz != null)
		fields
	}
	
	def getDeclaredMethodsIncludingSuperClasses(concreteClass: Class[_]) = {
		var clazz = concreteClass
		var methods = List[Method]()
		do {
			methods ++= clazz.getDeclaredMethods()
			clazz = clazz.getSuperclass()
		} while (clazz != null)
		methods
	}

	def set(obj: Object, fieldName: String, value: Object) = {
		val field = getDeclaredFieldsIncludingSuperClasses(obj.getClass()).filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.set(obj, value)
	}
	
	def get(obj: Object, fieldName: String) = {
		val field = getDeclaredFieldsIncludingSuperClasses(obj.getClass()).filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.get(obj)
	}
	
	def getStatic(obj: Class[_], fieldName: String) = {
		val field = getDeclaredFieldsIncludingSuperClasses(obj).filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.get(obj)
	}
	
	def invoke(obj: Object, methodName: String, params: Object*) = {
		val clazz = obj.getClass
		val method = clazz.getDeclaredMethods().filter(_.toString().contains(methodName)).head
		method.setAccessible(true)
		method.invoke(obj, params: _*)
	}
	
	def getAllImplementorsNames(interfaceName: String) =
		Set(new Reflections("").getStore.getSubTypesOf(interfaceName).toArray: _*).asInstanceOf[Set[String]]
	
	def findObject[R](obj: T forSome { type T <:  Any })(f: (Any) => Boolean): Set[R] = {
		(if(f(obj))
			Set(obj)
		else
			obj match {
				case seq: Seq[Any] =>
					(for(value <- seq)
						yield findObject(value)(f)).flatten.toSet
				case obj: Product =>
					(for(elem <- obj.productElements.toList)
						yield findObject(elem)(f)).flatten.toSet
				case other =>
					Set()
					
			}).asInstanceOf[Set[R]]
	}
	
	def deepCopyMapping[T, A <: Any, B <: Any](obj: T, map: Map[A, B]): T =
		deepCopyMapping(obj, MutableMap() ++ map)
	
	private[this] def deepCopyMapping[T, A <: Any, B <: Any](obj: T, map: MutableMap[A, B]): T = {
		val option = map.get(obj.asInstanceOf[A])
		if(option.nonEmpty) {
			val substitute = option.get.asInstanceOf[T]
			deepCopyMapping(substitute, map - obj.asInstanceOf[A])
		} else 
			(obj match {
				case seq: Seq[Any] =>
					for(elem <- seq; if(elem != Nil))
						yield deepCopyMapping(elem, map)
				case obj: Product =>
					val values =
					for(elem <- obj.productElements.toList)
						yield deepCopyMapping(elem, map)
					val constructor = obj.getClass.getConstructors().head
					val newInstance = constructor.newInstance(values.asInstanceOf[Seq[Object]]: _*)
					map += (obj -> newInstance).asInstanceOf[(A,B)]
					newInstance
				case other =>
					other
			}).asInstanceOf[T]
	}
}