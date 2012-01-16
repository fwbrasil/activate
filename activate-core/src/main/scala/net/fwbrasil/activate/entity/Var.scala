package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction

class Var[T](val _valueClass: Class[_], val name: String, _outerEntity: Entity)
		extends Ref[T](None)(_outerEntity.context)
		with java.io.Serializable {

	val outerEntity = _outerEntity
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
		if (outerEntity != null) outerEntity.initialize
		f
	}

	override def toString = name + " -> " + super.snapshot
}

class IdVar(outerEntity: Entity)
		extends Var[String](classOf[String], "id", outerEntity) {

	var id: String = _

	override def getRequiredTransaction: Transaction = null

	override def getTransaction: Option[Transaction] = None

	override def put(value: Option[String]): Unit = {
		id = value.getOrElse(null)
	}

	override def get =
		Option(id)

	override def destroy: Unit = {

	}

	override def toString = "id -> " + id
}