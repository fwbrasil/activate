package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.uuid.UUIDUtil

class Var[T](metadata: EntityPropertyMetadata, _outerEntity: Entity)
		extends Ref[T](None, true)(_outerEntity.context)
		with java.io.Serializable {

	val outerEntity = _outerEntity
	val name = metadata.name
	val isTransient = metadata.isTransient
	val tval = metadata.tval.asInstanceOf[Option[T] => EntityValue[T]]
	var initialized = false

	def toEntityPropertyValue(value: T) = tval(Option(value))
	def outerEntityClass = outerEntity.niceClass
	val valueClass = metadata.propertyType

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

