package net.fwbrasil.activate.index

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import scala.annotation.implicitNotFound
import scala.collection.JavaConversions.asScalaSet
import scala.collection.JavaConversions.bufferAsJavaList
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.mapAsScalaConcurrentMap
import scala.collection.JavaConversions.setAsJavaSet
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.LazyList
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.radon.transaction.NestedTransaction
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.scala.UnsafeLazy._
import scala.collection.mutable.HashMap
import scala.collection.JavaConversions._
import net.fwbrasil.radon.util.Lockable

case class MemoryIndex[E <: Entity: Manifest, T] private[index] (
    keyProducer: E => T, context: ActivateContext)
        extends Logging
        with Lockable {

    import context._

    private val index = new HashMap[T, Set[String]]()
    private val invertedIndex = new HashMap[String, T]()
    val entityClass = erasureOf[E]

    private var lazyInit = unsafeLazy(reload)

    def get(key: T) = {
        lazyInit.get
        doWithReadLock {
            val dirtyEntities =
                context.liveCache
                    .dirtyEntitiesFromTransaction(entityClass)
                    .filter(e => keyProducer(e) == key)
                    .map(_.id)
            val ids =
                index.get(key)
                    .map(_ ++ dirtyEntities)
                    .getOrElse(dirtyEntities)
                    .toList
            new LazyList(ids)
        }
    }

    private[index] def deleteEntities(entities: List[Entity]): Unit =
        deleteEntities(entities.map(_.id).toSet)

    private[index] def deleteEntities(ids: Set[String]) =
        doWithWriteLock {
            for (id <- ids) {
                invertedIndex.get(id).map {
                    key =>
                        index.get(key).map {
                            values =>
                                index.put(key, values -- Set(id))
                        }
                        invertedIndex.remove(id)
                }
            }
        }

    private[index] def updateEntities(entities: List[Entity]) = {
        deleteEntities(entities)
        doWithWriteLock {
            for (entity <- entities) {
                val key = keyProducer(entity.asInstanceOf[E])
                val value = index.getOrElseUpdate(key, Set())
                index.put(key, value ++ Set(entity.id))
                invertedIndex.put(entity.id, key)
            }
        }
    }

    private[index] def unload = {
        index.clear
        invertedIndex.clear
        lazyInit = unsafeLazy(reload)
    }

    private[index] def reload: Unit = {
        info(s"Reloading index $name")
        transactional {
            val entities =
                query {
                    (e: E) => where() select (e)
                }.toSet
            updateEntities(entities.filter(_.isPersisted).toList)
        }
        info(s"Index $name loaded")
    }

    private def name = context.memoryIndexName(this)

}

trait MemoryIndexContext {
    this: ActivateContext =>

    private val memoryIndexes = new ListBuffer[MemoryIndex[_, _]]()

    private def memoryIndexFields =
        this.getClass.getDeclaredFields.filter(e => classOf[MemoryIndex[_, _]].isAssignableFrom(e.getType))

    private def memoryIndexesNames =
        memoryIndexFields.map(e => { e.setAccessible(true); e }).map(field => (field.get(this), field.getName.split("$").last)).toMap

    private[activate] def memoryIndexName(index: MemoryIndex[_, _]) =
        memoryIndexesNames.getOrElse(index, "Unnamed")

    protected class MemoryIndexProducer[E <: Entity: Manifest] {
        def on[T](keyProducer: E => T) = {
            val index = new MemoryIndex[E, T](keyProducer, MemoryIndexContext.this)
            memoryIndexes += index
            index
        }
    }

    protected def memoryIndex[E <: Entity: Manifest] = new MemoryIndexProducer[E]

    private[activate] def updateMemoryIndexes(
        transaction: Transaction,
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity]) = {
        if (!memoryIndexes.isEmpty) {
            for (index <- memoryIndexes) {

                val entityClass = index.entityClass

                val filteredDeletes =
                    deletes.filter(insert => entityClass.isAssignableFrom(insert.getClass))
                if (filteredDeletes.nonEmpty)
                    index.deleteEntities(filteredDeletes)

                val filteredUpdates =
                    (updates ++ inserts).filter(insert => entityClass.isAssignableFrom(insert.getClass))

                val nested = new NestedTransaction(transaction)
                transactional(nested) {
                    if (filteredUpdates.nonEmpty)
                        index.updateEntities(filteredUpdates)
                }
                nested.rollback
            }
        }
    }

    private[activate] def unloadMemoryIndexes =
        memoryIndexes.foreach(_.unload)

} 