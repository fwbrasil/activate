package net.fwbrasil.activate.serialization

import scala.collection._

trait NamedSingletonSerializable extends java.io.Serializable {
	
	def name: String
	
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
		new mutable.HashMap[Class[_], mutable.HashMap[String, NamedSingletonSerializable]] 
        	with mutable.SynchronizedMap[Class[_], mutable.HashMap[String, NamedSingletonSerializable]] 
	
	def registerInstance(instance: NamedSingletonSerializable) =
		instances.getOrElseUpdate(
				instance.getClass, 
				new mutable.HashMap[String, NamedSingletonSerializable]()) += (instance.name -> instance)
	
}