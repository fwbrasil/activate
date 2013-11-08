package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.entity.LazyList
import net.fwbrasil.scala.UnsafeLazy._
import net.fwbrasil.radon.util.Lockable
import grizzled.slf4j.Logging
import net.fwbrasil.radon.transaction.Transaction
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.transaction.NestedTransaction

abstract class ActivateIndex[E <: Entity: Manifest, T](
    keyProducer: E => T,
    context: ActivateContext)
        extends Logging {

    context.indexes += this

    def name = context.indexName(this)

    val entityClass = erasureOf[E]

    private var lazyInit: UnsafeLazyItem[Unit] = _

    clearLazyInit

    private def clearLazyInit =
        lazyInit =
            unsafeLazy {
                info(s"Reloading index $name")
                reload
                info(s"Index $name reloaded")
            }

    private[index] def unload = {
        clearIndex
        clearLazyInit
    }

    private[index] def update(
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity]) =
        if (inserts.nonEmpty || updates.nonEmpty || deletes.nonEmpty) {
            lazyInit.get
            updateIndex(inserts, updates, deletes)
        }

    def get(key: T) = {
        lazyInit.get
        val dirtyEntities =
            context.liveCache
                .dirtyEntitiesFromTransaction(entityClass)
                .values

        val fromIndex =
            indexGet(key) -- dirtyEntities.map(_.id)

        val dirtyEntitiesFiltered =
            dirtyEntities
                .filter(e => !e.isDeleted && keyProducer(e) == key)
                .map(_.id)

        new LazyList((Set() ++ dirtyEntitiesFiltered ++ fromIndex).toList)
    }

    protected def reload: Unit
    protected def indexGet(key: T): Set[String]
    protected def clearIndex: Unit
    protected def updateIndex(
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity])

}

trait ActivateIndexContext extends MemoryIndexContext with PersistedIndexContext {
    this: ActivateContext =>

    private[index] val indexes = new ListBuffer[ActivateIndex[_, _]]()

    private def indexFields =
        this.getClass.getDeclaredFields.filter(e => classOf[ActivateIndex[_, _]].isAssignableFrom(e.getType))

    private def indexesNames =
        indexFields.map(e => { e.setAccessible(true); e }).map(field => (field.get(this), field.getName.split("$").last)).toMap

    private[activate] def indexName(index: ActivateIndex[_, _]) =
        indexesNames.getOrElse(index, throw new IllegalStateException)

    private[activate] def updateIndexes(
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity]) =
        for (index <- indexes) {

            val entityClass = index.entityClass

            def filter(entities: List[Entity]) =
                entities.filter(insert => entityClass.isAssignableFrom(insert.getClass))

            index.update(
                filter(inserts),
                filter(updates),
                filter(deletes))
        }

    private def filterEntities(index: ActivateIndex[_, _], entities: List[Entity]) = {
        val entityClass = index.entityClass
        entities.filter(insert => entityClass.isAssignableFrom(insert.getClass))
    }

    private[activate] def unloadIndexes =
        indexes.foreach(_.unload)
}