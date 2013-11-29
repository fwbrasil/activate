package net.fwbrasil.activate.statement

import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.EntityHelper
import java.util.IdentityHashMap
import scala.collection.JavaConversions._
import language.existentials

case class EntitySource(var entityClass: Class[E] forSome { type E <: BaseEntity }, name: String) {
    override def toString = name + ": " + EntityHelper.getEntityName(entityClass)
}

case class From(entitySources: EntitySource*) {
    override def toString = "(" + entitySources.mkString(", ") + ")"
}

object From {
    val entitySourceMapThreadLocal = new ThreadLocal[IdentityHashMap[BaseEntity, EntitySource]] {
        override def initialValue = new IdentityHashMap[BaseEntity, EntitySource]()
    }
    def entitySourceMap =
        entitySourceMapThreadLocal.get
    def entitySourceFor(entity: BaseEntity) =
        Option(entitySourceMapThreadLocal.get.get(entity))
    def nextAlias = "s" + (entitySourceMap.size + 1)
    def createAndRegisterEntitySource[E <: BaseEntity](clazz: Class[E], entity: E) =
        entitySourceMap.put(entity, EntitySource(clazz, nextAlias))
    private[this] def entitySources = entitySourceMap.values.toList
    def from =
        From(entitySources: _*)
    def clear =
        entitySourceMap.clear

    def runAndClearFrom[S <: Statement](f: => S) = {
        val old = entitySourceMap.clone.asInstanceOf[IdentityHashMap[BaseEntity, EntitySource]]
        val oldRefStack = StatementMocks._lastFakeVarCalled.get
        StatementMocks.clearFakeVarCalled
        clear
        try {
            f
        } finally {
            entitySourceMapThreadLocal.set(old)
            StatementMocks._lastFakeVarCalled.set(oldRefStack)
        }
    }
}