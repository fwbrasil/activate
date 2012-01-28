package net.fwbrasil.activate.serialization

import scala.collection._
import scala.collection.mutable.ListBuffer

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
		NamedSingletonSerializable.instances(clazz)(name)
}

object NamedSingletonSerializable {
	val instances =
		new mutable.HashMap[Class[_], mutable.HashMap[String, NamedSingletonSerializable]] with mutable.SynchronizedMap[Class[_], mutable.HashMap[String, NamedSingletonSerializable]]

	def instancesOf[T](clazz: Class[T]) = {
		val ret = new ListBuffer[T]
		for (map <- instances.values)
			for (instance <- map.values)
				if (clazz.isAssignableFrom(instance.getClass()))
					ret += instance.asInstanceOf[T]
		ret
	}

	def registerInstance(instance: NamedSingletonSerializable) = {
		val map = instances.getOrElseUpdate(
			instance.getClass,
			new mutable.HashMap[String, NamedSingletonSerializable]())
		val option = map.get(instance.name)
		if (option.isDefined && option.get != instance)
			throw new IllegalStateException("Duplicate singleton!")
		map += (instance.name -> instance)
	}

}