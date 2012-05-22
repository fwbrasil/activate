package net.fwbrasil.activate.entity

import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.uuid.UUIDUtil

class Var[T](value: Option[T], val isMutable: Boolean, val _valueClass: Class[_], val name: String, _outerEntity: Entity)
		extends Ref[T](value)(_outerEntity.context)
		with java.io.Serializable {

	def this(isMutable: Boolean, _valueClass: Class[_], name: String, _outerEntity: Entity) =
		this(None, isMutable, _valueClass, name, _outerEntity)

	val outerEntity = _outerEntity
	lazy val tval = EntityValue.tvalFunction[T](_valueClass)
	def toEntityPropertyValue(value: T) = tval(Option(value))
	def outerEntityClass = outerEntity.niceClass
	def valueClass = _valueClass

	override def get = doInitialized {
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

object IdVar {
	def generateId(outerEntity: Entity) = {
		val uuid = UUIDUtil.generateUUID
		val classId = EntityHelper.getEntityClassHashId(outerEntity.getClass)
		uuid + "-" + classId
	}
}

class IdVar(outerEntity: Entity)
		extends Var[String](Option(IdVar.generateId(outerEntity)), false, classOf[String], "id", outerEntity) {

	var id: String = _

	override def get =
		Some(id)

	override def put(value: Option[String]): Unit = {
		if (value != null && value.nonEmpty && id == null) {
			super.put(value)
			id = value.get
		}
	}

	override protected def doInitialized[A](f: => A): A = {
		f
	}

	override def toString = "id -> " + id
}