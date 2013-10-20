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
        extends ActivateIndex[E, T](keyProducer, context)
        with Logging
        with Lockable {

    import context._

    private val index = new HashMap[T, Set[String]]()
    private val invertedIndex = new HashMap[String, T]()

    override protected def indexGet(key: T) =
        index.get(key).getOrElse(Set())

    override protected def updateIndex(
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity]) = {
        insertEntities(inserts)
        updateEntities(updates)
        deleteEntities(deletes)
    }

    private def updateEntities(entities: List[Entity]) = {
        deleteEntities(entities)
        insertEntities(entities)
    }

    private def insertEntities(entities: List[Entity]) =
        for (entity <- entities) {
            val key = keyProducer(entity.asInstanceOf[E])
            val value = index.getOrElseUpdate(key, Set())
            index.put(key, value ++ Set(entity.id))
            invertedIndex.put(entity.id, key)
        }

    private def deleteEntities(entities: List[Entity]) =
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

    override protected def reload: Unit = {
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

    protected class MemoryIndexProducer[E <: Entity: Manifest] {
        def on[T](keyProducer: E => T) =
            new MemoryIndex[E, T](keyProducer, MemoryIndexContext.this)
    }

    protected def memoryIndex[E <: Entity: Manifest] = new MemoryIndexProducer[E]

} 