package net.fwbrasil.activate.query

import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.entity.Entity

case class EntitySource(var entityClass: Class[E] forSome { type E <: Entity }, name: String) {
	override def toString = name + ": " + entityClass.getSimpleName
}

case class From(entitySources: EntitySource*) {
	override def toString = "(" + entitySources.mkString(", ") + ")"
}

object From {
	val entitySourceMap = new ThreadLocal[MutableMap[Entity, EntitySource]] {
		override def initialValue = MutableMap[Entity, EntitySource]()
	}
	def entitySourceFor(entity: Entity) =
		entitySourceMap.get.get(entity)
	def nextAlias = "s" + (entitySourceMap.get.size + 1)
	def createAndRegisterEntitySource[E <: Entity](clazz: Class[E], entity: E) =
		entitySourceMap.get += (entity -> EntitySource(clazz, nextAlias))
	private[this] def entitySources = entitySourceMap.get.values.toList
	def from =
		From(entitySources: _*)
	def clear =
		entitySourceMap.get.clear

	def runAndClearFrom[S](f: => Query[S]) = {
		val old = entitySourceMap.get.clone
		clear
		try {
			f
		} finally {
			entitySourceMap.set(old)
		}
	}
}