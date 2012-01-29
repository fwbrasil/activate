package net.fwbrasil.activate.serialization

import scala.collection.mutable.{ HashMap => MutableHashMap, ListBuffer, SynchronizedMap }

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
		new MutableHashMap[Class[_], MutableHashMap[String, NamedSingletonSerializable]] with SynchronizedMap[Class[_], MutableHashMap[String, NamedSingletonSerializable]]

	def instancesOf[T <: NamedSingletonSerializable: Manifest]: List[T] =
		instancesOf(manifest[T].erasure.asInstanceOf[Class[T]])

	def instancesOf[T <: NamedSingletonSerializable](clazz: Class[T]): List[T] = {
		val ret = new ListBuffer[T]
		for (map <- instances.values)
			for (instance <- map.values)
				if (clazz.isAssignableFrom(instance.getClass()))
					ret += instance.asInstanceOf[T]
		ret.toList
	}

	def registerInstance(instance: NamedSingletonSerializable) = {
		val map = instances.getOrElseUpdate(
			instance.getClass,
			new MutableHashMap[String, NamedSingletonSerializable]())
		val option = map.get(instance.name)
		if (option.isDefined && option.get != instance)
			throw new IllegalStateException("Duplicate singleton!")
		map += (instance.name -> instance)
	}

}