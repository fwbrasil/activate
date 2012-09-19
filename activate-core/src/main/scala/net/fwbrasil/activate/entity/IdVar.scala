package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.uuid.UUIDUtil

object IdVar {
	def generateId(entityClass: Class[_]) = {
		val uuid = UUIDUtil.generateUUID
		val classId = EntityHelper.getEntityClassHashId(entityClass)
		uuid + "-" + classId
	}
}

class IdVar(outerEntity: Entity)
		extends Var[String](Option(IdVar.generateId(outerEntity.getClass)), false, false, classOf[String], "id", outerEntity) {

	var id: String = _

	override def getValue() =
		id

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