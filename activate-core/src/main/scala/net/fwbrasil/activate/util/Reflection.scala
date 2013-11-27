package net.fwbrasil.activate.util

import java.lang.annotation.Annotation
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Date

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.{ HashMap => MutableHashMap }
import scala.language.existentials
import scala.language.implicitConversions

import org.joda.time.base.AbstractInstant
import org.objenesis.ObjenesisStd
import org.reflections.Reflections

import net.fwbrasil.activate.entity.Entity

object Reflection {

    import language.implicitConversions

    val objenesis = new ObjenesisStd(false);

    implicit class NiceObject[T](val x: T) extends AnyVal {
        def niceClass: Class[T] = x.getClass.asInstanceOf[Class[T]]
    }

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
        val field = getDeclaredFieldsIncludingSuperClasses(obj.getClass).filter(_.getName() == fieldName).head
        field.setAccessible(true)
        field.set(obj, value)
    }

    def get(obj: Object, fieldName: String) = {
        val fields = getDeclaredFieldsIncludingSuperClasses(obj.getClass)
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
        val clazz = obj.getClass
        val method = clazz.getDeclaredMethods().filter(_.toString().contains(methodName)).head
        method.setAccessible(true)
        method.invoke(obj, params: _*)
    }

    val reflectionsCache = MutableHashMap[Set[Object], Reflections]()

    private def reflectionsHints(classpathHints: List[Any]) =
        (classpathHints.map {
            _ match {
                case clazz: Class[_] if(clazz.getPackage == null) =>
                    clazz.getClassLoader
                case clazz: Class[_] =>
                    clazz.getPackage.getName
                case packag: String =>
                    packag
            }
        }).toSet

    private def reflectionsFor(hints: Set[Object]) =
        reflectionsCache.synchronized {
            reflectionsCache.getOrElseUpdate(hints, new Reflections(hints.toArray[Object]))
        }

    def getAllImplementorsNames(classpathHints: List[Any], interfaceClass: Class[_]) = {
        val hints = reflectionsHints(classpathHints ++ List(interfaceClass))
        val reflections = reflectionsFor(hints)
        val subtypes = reflections.getStore.getSubTypesOf(interfaceClass.getName).toArray
        Set(subtypes: _*).asInstanceOf[Set[String]]
    }

    import language.existentials

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

    def deepCopyMapping[T, A <: Any, B <: Any](obj: T, map: java.util.IdentityHashMap[A, B]): T = {
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
                    val constructors = obj.getClass.getConstructors
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
    
    def getObjectOption[T](clazz: Class[_]) = {
        clazz.getFields.find(_.getName == "MODULE$").map(_.get(clazz)).asInstanceOf[Option[T]]
    }
    
    def getCompanionObject[T](clazz: Class[_]) = {
        val companionClassOption =
            try {
                Option(clazz.getClassLoader.loadClass(clazz.getName + "$"))
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

    private val bitmapFieldsCache = new TrieMap[Class[_], List[Field]]()

    def initializeBitmaps(res: Any) =
        setBitmaps(res, initializedBitmapValuesMap)

    def uninitializeBitmaps(res: Any) =
        setBitmaps(res, uninitializedBitmapValuesMap)

    private val initializedBitmapValuesMap =
        Map[Class[_], Any](classOf[Boolean] -> true, classOf[Int] -> initializedInt, classOf[Byte] -> initializedByte)

    private val uninitializedBitmapValuesMap =
        Map[Class[_], Any](classOf[Boolean] -> false, classOf[Int] -> 0, classOf[Byte] -> 0.toByte)
        
    private def initializedByte =
        (0.toByte | 0x1 | 0x2 | 0x4 | 0x8 | 0x10 | 0x20 | 0x40 | 0x80).toByte
        
    private def initializedInt = {
        var int = 0
        for(i <- 0 to 31)
            int = int | Math.pow(2, i).intValue
        int
    }

    private def setBitmaps(res: Any, valuesMap: Map[Class[_], Any]) = {
        val clazz = res.getClass
        val fields =
            bitmapFieldsCache.getOrElseUpdate(
                clazz,
                getDeclaredFieldsIncludingSuperClasses(res.getClass)
                    .filter(field => !Modifier.isTransient(field.getModifiers) &&
                        field.getName.startsWith("bitmap$") && 
                        valuesMap.contains(field.getType)))
        for (field <- fields) {
            field.setAccessible(true)
            field.set(res, valuesMap(field.getType))
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