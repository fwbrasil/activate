package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.uuid.UUIDUtil

class Var[T](value: Option[T], val isMutable: Boolean, val isTransient: Boolean, val _valueClass: Class[_], val name: String, _outerEntity: Entity)
		extends Ref[T](value)(_outerEntity.context)
		with java.io.Serializable {

	def this(isMutable: Boolean, isTransient: Boolean, _valueClass: Class[_], name: String, _outerEntity: Entity) =
		this(None, isMutable, isTransient, _valueClass, name, _outerEntity)

	val outerEntity = _outerEntity
	lazy val tval =
		if (name == "id")
			EntityValue.tvalFunction[T](classOf[String], classOf[Object])
		else
			EntityHelper.getEntityMetadata(outerEntityClass).propertiesMetadata.find(_.name == name).get.tval.asInstanceOf[Option[T] => EntityValue[T]]
	def toEntityPropertyValue(value: T) = tval(Option(value))
	def outerEntityClass = outerEntity.niceClass
	def valueClass = _valueClass

	override def get = doInitialized {
		if (outerEntity == null)
			throw new IllegalStateException("Var isnt bound to an Entity.")
		super.get
	}

	// Better performance than use Source.!
	def getValue() =
		get.getOrElse(null.asInstanceOf[T])
	def putValue(value: T) =
		put(Option(value))

	override def put(value: Option[T]): Unit = doInitialized {
		super.put(value)
	}

	override def destroy: Unit = doInitialized {
		super.destroy
	}

	override def isDestroyed: Boolean = doInitialized {
		super.isDestroyed
	}

	private[activate] def isDestroyedSnapshot: Boolean = {
		super.isDestroyed
	}

	protected def doInitialized[A](f: => A): A = {
		if (outerEntity != null) outerEntity.initialize
		f
	}

	override def toString = name + " -> " + get.getOrElse("")
}

