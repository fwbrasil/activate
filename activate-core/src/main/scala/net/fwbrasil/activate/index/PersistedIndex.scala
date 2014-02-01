package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity._
import scala.collection.immutable.HashSet
import scala.collection.mutable.HashMap
import grizzled.slf4j.Logging
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.scala.UnsafeLazy._
import scala.collection.mutable.ListBuffer

class PersistedIndex[E <: BaseEntity: Manifest, P <: PersistedIndexEntry[K, E]: Manifest, K] private[index] (
    keyProducer: E => K, indexEntryProducer: K => P, context: ActivateContext, preloadEntries: Boolean)
        extends ActivateIndex[E, K](keyProducer, context)
        with Logging
        with Lockable {

    import context._

    private val index = new HashMap[K, P#ID]()
    private val invertedIndex = new HashMap[E#ID, P#ID]()

    override protected def reload: Unit =
        if (preloadEntries) {
            val entries =
                transactional(shadow) {
                    for (
                        entry <- all[P];
                        id <- entry.ids
                    ) yield {
                        (entry.key, id, entry)
                    }
                }
            doWithWriteLock {
                for ((key, entityId, entry) <- entries) {
                    invertedIndex.put(entityId, entry.id)
                    index.put(key, entry.id)
                }
            }
        }

    override protected def indexGet(key: K): Set[E#ID] = {
        val entryId =
            doWithReadLock {
                index.get(key)
            }.getOrElse {
                doWithWriteLock {
                    index.getOrElseUpdate(key, indexEntryProducer(key).id)
                }
            }
        entry(entryId).ids
    }

    override protected def clearIndex = {
        index.clear
        invertedIndex.clear
    }

    private val updatingIndex = new ThreadLocal[Boolean] {
        override def initialValue = false
    }

    override protected def updateIndex(
        inserts: List[E],
        updates: List[E],
        deletes: List[E]) = {
        if (!updatingIndex.get && (inserts.nonEmpty || updates.nonEmpty || deletes.nonEmpty)) {
            doWithWriteLock {
                val idsDelete = ListBuffer[E#ID]()
                val updatedEntries = ListBuffer[(K, E#ID, P#ID)]()
                updatingIndex.set(true)
                try transactional {
                    deleteEntities(deletes, idsDelete)
                    insertEntities(inserts, updatedEntries)
                    updateEntities(updates, idsDelete, updatedEntries)
                }
                finally updatingIndex.set(false)
                for (id <- idsDelete)
                    invertedIndex.remove(id)
                for ((key, entityId, entryId) <- updatedEntries) {
                    invertedIndex.put(entityId, entryId)
                    index.put(key, entryId)
                }
            }
        }
    }

    private def updateEntities(
        entities: List[E],
        idsDelete: ListBuffer[E#ID],
        updatedEntries: ListBuffer[(K, E#ID, P#ID)]) = {
        for (entity <- entities) {
            val key = keyProducer(entity.asInstanceOf[E])
            val entryId = index.getOrElse(key, indexEntryProducer(key).id)
            val entityId = entity.id
            val currentEntryOption = invertedIndex.get(entityId)
            val needsUpdate =
                currentEntryOption
                    .map(_ != entryId)
                    .getOrElse(true)
            if (needsUpdate) {
                currentEntryOption.map { oldEntry =>
                    entry(oldEntry).ids -= entityId
                    idsDelete += entityId
                }
                entry(entryId).ids += entityId
                updatedEntries += ((key, entity.id, entryId))
            }
        }
    }

    private def insertEntities(
        entities: List[E],
        updatedEntries: ListBuffer[(K, E#ID, P#ID)]) = {
        for (entity <- entities) {
            val key = keyProducer(entity.asInstanceOf[E])
            val entryId = index.getOrElse(key, indexEntryProducer(key).id)
            entry(entryId).ids += entity.id
            updatedEntries += ((key, entity.id, entryId))
        }
    }

    private def deleteEntities(
        entities: List[E],
        idsDelete: ListBuffer[E#ID]) =
        for (entity <- entities) {
            val id = entity.id
            invertedIndex.get(id).map {
                entryId =>
                    entry(entryId).ids -= id
            }
            idsDelete += id
        }

    private def entry(id: P#ID) =
        byId[P](id).getOrElse(
            throw new IllegalStateException(
                "Invalid persisted index entry id. If you dind't do direct manipulation to the database, " +
                    "please fill a bug report with this exception."))

    override def toString =
        transactional(new Transaction()(context)) {
            (for ((key, entryId) <- index) yield {
                key + " -> " + entry(entryId).ids
            }).mkString(",")
        }

}

trait PersistedIndexEntry[K, V <: BaseEntity] extends BaseEntity {
    def key: K
    var ids: HashSet[V#ID]
}

trait PersistedIndexContext {
    this: ActivateContext =>

    type PersistedIndexEntry[K, V <: BaseEntity] = net.fwbrasil.activate.index.PersistedIndexEntry[K, V]

    protected class PersistedIndexProducer0[E <: BaseEntity: Manifest](preloadEntries: Boolean) {
        def on[K](keyProducer: E => K) =
            new PersistedIndexProducer1[E, K](keyProducer, preloadEntries)
    }

    protected class PersistedIndexProducer1[E <: BaseEntity: Manifest, K](keyProducer: E => K, preloadEntries: Boolean) {
        def using[P <: PersistedIndexEntry[K, E]: Manifest](indexEntityProducer: K => P) =
            new PersistedIndex[E, P, K](keyProducer, indexEntityProducer, PersistedIndexContext.this, preloadEntries)
    }

    protected def persistedIndex[E <: BaseEntity: Manifest] = new PersistedIndexProducer0[E](preloadEntries = true)
    protected def persistedIndex[E <: BaseEntity: Manifest](preloadEntries: Boolean) = new PersistedIndexProducer0[E](preloadEntries)

} 
