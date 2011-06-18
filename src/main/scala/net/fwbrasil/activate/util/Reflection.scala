package net.fwbrasil.activate.util

import tools.scalap.scalax.rules.scalasig._
import scala.runtime._
import org.objenesis._
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.GenericArrayType
import org.reflections.Reflections

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
	
	val ByteClass = classOf[scala.Byte]
	val ShortClass = classOf[scala.Short]
	val CharClass = classOf[scala.Char]
	val IntClass = classOf[scala.Int]
	val LongClass = classOf[scala.Long]
	val FloatClass = classOf[scala.Float]
	val DoubleClass = classOf[scala.Double]
	val BooleanClass = classOf[scala.Boolean]
	val NullClass = classOf[scala.Null]
	val UnitClass = classOf[scala.Unit]
	
	def getScalaSig(clazz: Class[_]) = {
		val split = clazz.getName.split('$')
		val baseClass = Class.forName(split(0))
		val sigOption = ScalaSigParser.parse(baseClass)
		if(sigOption == None)
			throw new IllegalStateException("Scala signature not found for class {0} using base class {1}.".format(clazz, baseClass))
		val sigBaseClass = sigOption.get.topLevelClasses.filter(_.name == baseClass.getSimpleName).head
		var sigClazz = sigBaseClass
		for(sigInner <- split.tail)
			sigClazz = sigClazz.children.filter(_.name == sigInner).head.asInstanceOf[ClassSymbol]
		sigClazz
	}

	
	// TODO reimplementar, esta especifico para os tipos atuais de atributos
	def getEntityFieldTypeArgument(sig: => ClassSymbol, field: Field) = {
		val genericType = field.getGenericType
		val arguments = genericType.asInstanceOf[ParameterizedType].getActualTypeArguments
		if(arguments.size!=1)
			throw new IllegalStateException("There should be only one type argument")
		val jclazz = arguments(0) match {
			case genericArrayType: GenericArrayType =>
				classOf[Array[Byte]]
			case parameterizedType: ParameterizedType =>
				parameterizedType.getRawType.asInstanceOf[Class[_]]
			case clazz: Class[_] =>
				clazz
		}
		if (jclazz == classOf[java.lang.Object]) {
			val method = sig.children.filter(_.name == field.getName).head.asInstanceOf[MethodSymbol]
			val a = method.infoType.asInstanceOf[NullaryMethodType]
			val r = a.resultType.asInstanceOf[TypeRefType]
			val x = r.typeArgs.head.asInstanceOf[TypeRefType].symbol.toString
			x match {
				case "scala.Byte" => classOf[Byte]
				case "scala.Short" => classOf[Short]
				case "scala.Char" => classOf[Char]
				case "scala.Int" => classOf[Int]
				case "scala.Long" => classOf[Long]
				case "scala.Float" => classOf[Float]
				case "scala.Double" => classOf[Double]
				case "scala.Boolean" => classOf[Boolean]
				case x => Class.forName(x)
			}
		} else jclazz
	}
	
	def getAllImplementors[E: Manifest] = 
		Set(new Reflections("").getSubTypesOf(manifest[E].erasure).toArray: _*).asInstanceOf[Set[Class[E]]]
	
}