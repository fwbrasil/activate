package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.util.Reflection.toNiceObject

class Var[T](val _valueClass: Class[_], val name: String, _outerEntity: Entity)
		extends Ref[T](None)(_outerEntity.context)
		with java.io.Serializable {

	val outerEntity = _outerEntity
	val tval = EntityValue.tvalFunction[T](_valueClass)
	def toEntityPropertyValue(value: T) = tval(Option(value))
	def outerEntityClass = outerEntity.niceClass
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

class IdVar(outerEntity: Entity)
		extends Var[String](classOf[String], "id", outerEntity) {

	var id: String = _

	override def put(value: Option[String]): Unit = {
		id = value.getOrElse(null)
		super.put(value)
	}

	override def get =
		Option(id)

	override protected def doInitialized[A](f: => A): A = {
		f
	}

	override def toString = "id -> " + id
}