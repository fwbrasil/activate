package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext

class Var[T](val _valueClass: Class[_], val name: String, val outerEntity: Entity)
    extends Ref[T](None)(outerEntity.context) 
    	with java.io.Serializable {

	val tval = EntityValue.tvalFunction(_valueClass).asInstanceOf[Option[T] => EntityValue[T]]
	def toEntityPropertyValue(value: Any) = tval(Option(value).asInstanceOf[Option[T]])
	def outerEntityClass = outerEntity.getClass.asInstanceOf[Class[_ <: Entity]]
	def valueClass = _valueClass
	
	override def get = doInitialized {
		super.get
	}

	override def put(value: Option[T]): Unit = doInitialized {
		super.put(value)
	}
	
	override def destroy: Unit = doInitialized {
	    super.destroy
	}
	
	private[this] def doInitialized[A](f: => A): A = {
		if(outerEntity!=null)outerEntity.initialize
		f
	}
	
	override def toString = name + " -> " + super.toString
}


import net.fwbrasil.activate.serialization.NamedSingletonSerializable
