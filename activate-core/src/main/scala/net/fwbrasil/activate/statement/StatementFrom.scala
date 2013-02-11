package net.fwbrasil.activate.statement

import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import java.util.IdentityHashMap
import scala.collection.JavaConversions._
import language.existentials

case class EntitySource(var entityClass: Class[E] forSome { type E <: Entity }, name: String) {
    override def toString = name + ": " + EntityHelper.getEntityName(entityClass)
}

case class From(entitySources: EntitySource*) {
    override def toString = "(" + entitySources.mkString(", ") + ")"
}

object From {
    val entitySourceMapThreadLocal = new ThreadLocal[IdentityHashMap[Entity, EntitySource]] {
        override def initialValue = new IdentityHashMap[Entity, EntitySource]()
    }
    def entitySourceMap =
        entitySourceMapThreadLocal.get
    def entitySourceFor(entity: Entity) =
        Option(entitySourceMapThreadLocal.get.get(entity))
    def nextAlias = "s" + (entitySourceMap.size + 1)
    def createAndRegisterEntitySource[E <: Entity](clazz: Class[E], entity: E) =
        entitySourceMap.put(entity, EntitySource(clazz, nextAlias))
    private[this] def entitySources = entitySourceMap.values.toList
    def from =
        From(entitySources: _*)
    def clear =
        entitySourceMap.clear

    def runAndClearFrom[S <: Statement](f: => S) = {
        val old = entitySourceMap.clone.asInstanceOf[IdentityHashMap[Entity, EntitySource]]
        clear
        try {
            f
        } finally {
            entitySourceMapThreadLocal.set(old)
        }
    }
}