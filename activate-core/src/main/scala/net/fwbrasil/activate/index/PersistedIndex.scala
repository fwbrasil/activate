package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity._
import scala.collection.immutable.HashSet
import scala.collection.mutable.HashMap
import grizzled.slf4j.Logging
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.scala.UnsafeLazy._
import scala.collection.mutable.ListBuffer

class PersistedIndex[E <: Entity: Manifest, P <: PersistedIndexEntry[K]: Manifest, K] private[index] (
    keyProducer: E => K, indexEntityProducer: K => P, context: ActivateContext)
        extends ActivateIndex[E, K](keyProducer, context)
        with Logging
        with Lockable {

    import context._

    private val index = new HashMap[K, P]()
    private val invertedIndex = new HashMap[String, P]()

    override protected def reload: Unit =
        doWithWriteLock {
            val entries =
                transactional(transient) {
                    for (
                        entry <- all[P];
                        id <- entry.ids
                    ) yield {
                        (entry.key, id, entry)
                    }
                }
            for ((key, entityId, entry) <- entries) {
                invertedIndex.put(entityId, entry)
                index.put(key, entry)
            }
        }

    override protected def indexGet(key: K): Set[String] = {
        val entry =
            doWithReadLock {
                index.get(key)
            }.getOrElse {
                doWithWriteLock {
                    index.getOrElseUpdate(key, indexEntityProducer(key))
                }
            }
        entry.ids
    }

    override protected def clearIndex = {
        index.clear
        invertedIndex.clear
    }

    private val updatingIndex = new ThreadLocal[Boolean] {
        override def initialValue = false
    }

    override protected def updateIndex(
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity]) = {
        if (!updatingIndex.get && (inserts.nonEmpty || updates.nonEmpty || deletes.nonEmpty))
            doWithWriteLock {
                val idsDelete = ListBuffer[String]()
                val updatedEntries = ListBuffer[(K, String, P)]()
                updatingIndex.set(true)
                try transactional {
                    deleteEntities(deletes, idsDelete)
                    insertEntities(inserts, updatedEntries)
                    updateEntities(updates, idsDelete, updatedEntries)
                }
                finally updatingIndex.set(false)
                for (id <- idsDelete)
                    invertedIndex.remove(id)
                for ((key, entityId, entry) <- updatedEntries) {
                    invertedIndex.put(entityId, entry)
                    index.put(key, entry)
                }
            }
    }

    private def updateEntities(
        entities: List[Entity],
        idsDelete: ListBuffer[String],
        updatedEntries: ListBuffer[(K, String, P)]) = {
        deleteEntities(entities, idsDelete)
        insertEntities(entities, updatedEntries)
    }

    private def insertEntities(
        entities: List[Entity],
        updatedEntries: ListBuffer[(K, String, P)]) = {
        for (entity <- entities) {
            val key = keyProducer(entity.asInstanceOf[E])
            val entry = index.getOrElse(key, indexEntityProducer(key))
            entry.ids += entity.id
            updatedEntries += ((key, entity.id, entry))
        }
    }

    private def deleteEntities(
        entities: List[Entity],
        idsDelete: ListBuffer[String]) =
        for (entity <- entities) {
            val id = entity.id
            invertedIndex.get(id).map {
                _.ids -= id
            }
            idsDelete += id
        }

}

trait PersistedIndexEntry[K] extends Entity {
    def key: K
    var ids: HashSet[String]
}

trait PersistedIndexContext {
    this: ActivateContext =>

    type PersistedIndexEntry[K] = net.fwbrasil.activate.index.PersistedIndexEntry[K]

    protected class PersistedIndexProducer0[E <: Entity: Manifest] {
        def on[K](keyProducer: E => K) =
            new PersistedIndexProducer1[E, K](keyProducer)
    }

    protected class PersistedIndexProducer1[E <: Entity: Manifest, K](keyProducer: E => K) {
        def using[P <: PersistedIndexEntry[K]: Manifest](indexEntityProducer: K => P) =
            new PersistedIndex[E, P, K](keyProducer, indexEntityProducer, PersistedIndexContext.this)
    }

    protected def persistedIndex[E <: Entity: Manifest] = new PersistedIndexProducer0[E]

} 
