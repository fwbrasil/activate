package net.fwbrasil.activate.index

import scala.annotation.implicitNotFound
import scala.collection.JavaConversions._
import scala.collection.JavaConversions.asScalaSet
import scala.collection.JavaConversions.bufferAsJavaList
import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.collection.JavaConversions.mapAsScalaConcurrentMap
import scala.collection.JavaConversions.setAsJavaSet
import scala.collection.mutable.HashMap

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.radon.transaction.NestedTransaction
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.scala.UnsafeLazy._

case class MemoryIndex[E <: BaseEntity: Manifest, T] private[index] (
    keyProducer: E => T, context: ActivateContext)
        extends ActivateIndex[E, T](keyProducer, context)
        with Logging
        with Lockable {

    import context._

    private val index = new HashMap[T, Set[E#ID]]()
    private val invertedIndex = new HashMap[E#ID, T]()

    override protected def indexGet(key: T) =
        doWithReadLock {
            index.get(key).getOrElse(Set())
        }

    override protected def updateIndex(
        inserts: List[E],
        updates: List[E],
        deletes: List[E]) =
        doWithWriteLock {
            transactional(new Transaction()(context)) {
                insertEntities(inserts)
                updateEntities(updates)
                deleteEntities(deletes)
            }
        }

    private def updateEntities(entities: List[E]) = {
        deleteEntities(entities)
        insertEntities(entities)
    }

    private def insertEntities(entities: List[E]) =
        for (entity <- entities) {
            val key = 
                try keyProducer(entity.asInstanceOf[E])
                catch {
                    case e: Throwable =>
                        context.retry()
                        throw e
                }
            val value = index.getOrElseUpdate(key, Set())
            index.put(key, value ++ Set(entity.id))
            invertedIndex.put(entity.id, key)
        }

    private def deleteEntities(entities: List[E]) =
        for (entity <- entities) {
            val id = entity.id
            invertedIndex.get(id).map {
                key =>
                    val values = index(key)
                    index.put(key, values -- Set(id))
                    invertedIndex.remove(id)
            }
        }

    override protected def clearIndex = {
        index.clear
        invertedIndex.clear
    }

    override protected def reload: Unit =
        doWithWriteLock {
            transactional(transient) {
                val entities =
                    query {
                        (e: E) => where() select (e)
                    }.toSet
                updateEntities(entities.filter(_.isPersisted).toList)
            }
        }
}

trait MemoryIndexContext {
    this: ActivateContext =>

    protected class MemoryIndexProducer[E <: BaseEntity: Manifest] {
        def on[T](keyProducer: E => T) =
            new MemoryIndex[E, T](keyProducer, MemoryIndexContext.this)
    }

    protected def memoryIndex[E <: BaseEntity: Manifest] = new MemoryIndexProducer[E]

} 