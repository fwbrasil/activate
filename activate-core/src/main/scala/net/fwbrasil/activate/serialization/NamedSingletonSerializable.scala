package net.fwbrasil.activate.serialization

import scala.collection.mutable.{ HashMap => MutableHashMap, ListBuffer, SynchronizedMap, HashSet => MutableHashSet }
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.util.ManifestUtil.manifestClass

trait NamedSingletonSerializable extends java.io.Serializable {

	private[activate] def name: String

	NamedSingletonSerializable.registerInstance(this)

	protected def writeReplace(): AnyRef =
		new NamedSingletonSerializableWrapper(this)

}

class NamedSingletonSerializableWrapper(instance: NamedSingletonSerializable) extends java.io.Serializable {
	val name = instance.name
	val clazz = instance.getClass
	protected def readResolve(): Any =
		NamedSingletonSerializable.instances(clazz.getName)(name)
}

object NamedSingletonSerializable {
	val instances =
		new MutableHashMap[String, MutableHashMap[String, NamedSingletonSerializable]] with SynchronizedMap[String, MutableHashMap[String, NamedSingletonSerializable]]

	private[this] def instancesMapOf[T: Manifest] =
		instances.getOrElseUpdate(
			erasureOf[T].getName,
			new MutableHashMap[String, NamedSingletonSerializable]()).asInstanceOf[MutableHashMap[String, T]]

	def instancesOf[T <: NamedSingletonSerializable: Manifest] = {
		val ret = MutableHashSet[T]()
		for ((clazz, instancesMap) <- instances; if (erasureOf[T].isAssignableFrom(Class.forName(clazz))))
			ret ++= instancesMap.values.asInstanceOf[Iterable[T]]
		ret
	}

	def registerInstance[T <: NamedSingletonSerializable](instance: T) = {
		implicit val m = manifestClass[T](instance.getClass)
		val map = instancesMapOf[T]
		val option = map.get(instance.name)
		//		if (option.isDefined && option.get != instance)
		//			warn("Duplicate singleton!")
		map += (instance.name -> instance)
	}

}