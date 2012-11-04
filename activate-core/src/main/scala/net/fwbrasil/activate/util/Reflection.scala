package net.fwbrasil.activate.util

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Date
import java.util.IdentityHashMap
import org.joda.time.base.AbstractInstant
import org.objenesis.ObjenesisStd
import org.reflections.Reflections
import javassist.bytecode.LocalVariableAttribute
import javassist.ClassClassPath
import javassist.ClassPool
import javassist.CtBehavior
import net.fwbrasil.activate.entity.Entity
import javassist.CtClass
import javassist.CtPrimitiveType
import org.reflections.util.ConfigurationBuilder
import org.reflections.util.FilterBuilder
import org.reflections.util.ClasspathHelper
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.scanners.Scanner
import org.reflections.scanners.AbstractScanner
import org.reflections.adapters.MetadataAdapter
import scala.collection.mutable.{ HashMap => MutableHashMap }
import scala.collection.mutable.SynchronizedMap
import net.fwbrasil.radon.util.Lockable
import java.lang.annotation.Annotation
import net.fwbrasil.activate.ActivateContext

object Reflection {

	val objenesis = new ObjenesisStd(false);

	class NiceObject[T](x: T) {
		def niceClass: Class[T] = x.getClass.asInstanceOf[Class[T]]
	}

	implicit def toNiceObject[T](x: T): NiceObject[T] = new NiceObject(x)

	class RichClass[T](clazz: Class[T]) {
		def isConcreteClass = !Modifier.isAbstract(clazz.getModifiers) && !clazz.isInterface
		def niceConstructors: List[Constructor[T]] = clazz.getConstructors.toList.asInstanceOf[List[Constructor[T]]]
	}

	implicit def toRichClass[T](clazz: Class[T]) = new RichClass(clazz)

	def newInstance[T](clazz: Class[T]): T = {
		val res = newInstanceUnitialized(clazz)
		initializeBitmaps(res)
		res
	}

	def newInstanceUnitialized[T](clazz: Class[T]): T = {
		objenesis.newInstance(clazz).asInstanceOf[T]
	}

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
		val field = getDeclaredFieldsIncludingSuperClasses(obj.niceClass).filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.set(obj, value)
	}

	def get(obj: Object, fieldName: String) = {
		val fields = getDeclaredFieldsIncludingSuperClasses(obj.niceClass)
		val field = fields.filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.get(obj)
	}

	def getStatic(obj: Class[_], fieldName: String) = {
		val fieldOption = getDeclaredFieldsIncludingSuperClasses(obj).filter(_.getName() == fieldName).headOption
		if (fieldOption.isDefined) {
			val field = fieldOption.get
			field.setAccessible(true)
			field.get(obj)
		} else null
	}

	def invoke(obj: Object, methodName: String, params: Object*) = {
		val clazz = obj.niceClass
		val method = clazz.getDeclaredMethods().filter(_.toString().contains(methodName)).head
		method.setAccessible(true)
		method.invoke(obj, params: _*)
	}

	val reflectionsCache = MutableHashMap[Set[Object], Reflections]()

	private def reflectionsHints(classes: List[Class[_]]) =
		(classes.map {
			clazz =>
				if (clazz.getPackage == null)
					clazz.getClassLoader
				else
					clazz.getPackage.getName
		}).toSet

	private def reflectionsFor(hints: Set[Object]) =
		reflectionsCache.synchronized {
			reflectionsCache.getOrElseUpdate(hints, new Reflections(hints.toArray[Object]))
		}

	def getAllImplementorsNames(pointsOfView: List[Class[_]], interfaceClass: Class[_]) = {
		val hints = reflectionsHints(pointsOfView ++ List(interfaceClass))
		val reflections = reflectionsFor(hints)
		val subtypes = reflections.getStore.getSubTypesOf(interfaceClass.getName).toArray
		Set(subtypes: _*).asInstanceOf[Set[String]]
	}

	def findObject[R](obj: T forSome { type T <: Any })(f: (Any) => Boolean): Set[R] = {
		(if (f(obj))
			Set(obj)
		else
			obj match {
				case seq: Seq[_] =>
					(for (value <- seq)
						yield findObject(value)(f)).flatten.toSet
				case obj: Product =>
					(for (elem <- obj.productIterator.toList)
						yield findObject(elem)(f)).flatten.toSet
				case other =>
					Set()

			}).asInstanceOf[Set[R]]
	}

	def deepCopyMapping[T, A <: Any, B <: Any](obj: T, map: IdentityHashMap[A, B]): T = {
		val substitute = map.get(obj.asInstanceOf[A])
		if (substitute != null) {
			substitute.asInstanceOf[T]
		} else
			(obj match {
				case seq: Seq[_] =>
					for (elem <- seq; if (elem != Nil))
						yield deepCopyMapping(elem, map)
				case obj: Enumeration#Value =>
					obj
				case obj: Entity =>
					obj
				case obj: Product =>
					val values =
						for (elem <- obj.productIterator.toList)
							yield deepCopyMapping(elem, map)
					val constructors = obj.niceClass.getConstructors
					val constructorOption = constructors.headOption
					if (constructorOption.isDefined) {
						val constructor = constructorOption.get
						val newInstance = constructor.newInstance(values.asInstanceOf[Seq[Object]]: _*)
						map.put(obj.asInstanceOf[A], newInstance.asInstanceOf[B])
						newInstance
					} else obj
				case other =>
					other
			}).asInstanceOf[T]
	}

	def getObject[T](clazz: Class[_]) = {
		clazz.getField("MODULE$").get(clazz).asInstanceOf[T]
	}

	def getCompanionObject[T](clazz: Class[_]) = {
		val companionClassOption =
			try {
				Option(Class.forName(clazz.getName + "$"))
			} catch {
				case e: ClassNotFoundException =>
					None
			}
		companionClassOption.map(_.getField("MODULE$").get(clazz)).asInstanceOf[Option[T]]
	}

	def materializeJodaInstant(clazz: Class[_], date: Date): AbstractInstant = {
		val constructors = clazz.getDeclaredConstructors()
		val constructor = constructors.find((c: Constructor[_]) => {
			val paramTypes = c.getParameterTypes()
			paramTypes.size == 1 && paramTypes.head.toString == "long"
		}).get
		val params: Seq[Object] = Seq(date.getTime.asInstanceOf[Object])
		val materialized = constructor.newInstance(params: _*)
		materialized.asInstanceOf[AbstractInstant]
	}

	private val bitmapFieldsCache = new MutableHashMap[Class[_], List[Field]]() with Lockable

	def initializeBitmaps(res: Any) =
		setBitmaps(res, Integer.MAX_VALUE)

	def uninitializeBitmaps(res: Any) =
		setBitmaps(res, 0)

	private def setBitmaps(res: Any, value: Int) = {
		val clazz = res.getClass
		val fields =
			bitmapFieldsCache.doWithReadLock {
				bitmapFieldsCache.get(clazz)
			}.getOrElse {
				bitmapFieldsCache.doWithWriteLock {
					bitmapFieldsCache.getOrElseUpdate(
						clazz,
						getDeclaredFieldsIncludingSuperClasses(res.getClass)
							.filter(field => field.getName.startsWith("bitmap$") && field.getType == classOf[Int]))
				}
			}
		for (field <- fields) {
			field.setAccessible(true)
			field.set(res, value)
		}
		res
	}

	def hasClassAnnotationInHierarchy(pClazz: Class[_], annotationClass: Class[_ <: Annotation]): Boolean = {
		var clazz = pClazz
		var annotations = List[Annotation]()
		do {
			if (clazz.getAnnotation(annotationClass) != null)
				return true
			clazz = clazz.getSuperclass()
		} while (clazz != null)
		return false
	}

}