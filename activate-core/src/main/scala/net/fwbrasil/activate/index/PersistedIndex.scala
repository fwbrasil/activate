package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity._
import scala.collection.immutable.HashSet
import scala.collection.mutable.HashMap
import grizzled.slf4j.Logging
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.scala.UnsafeLazy._

class PersistedIndex[E <: Entity: Manifest, P <: PersistedIndexEntry[K]: Manifest, K] private[index] (
    keyProducer: E => K, indexEntityProducer: K => P, context: ActivateContext)
        extends ActivateIndex[E, K](keyProducer, context)
        with Logging
        with Lockable {

    import context._

    private val index = new HashMap[K, P]()
    private val invertedIndex = new HashMap[String, P]()

    override protected def reload: Unit =
        transactional {
            for (entity <- all[P]) {
                index += entity.key -> entity
                for (id <- entity.ids)
                    invertedIndex += id -> entity
            }
        }

    override protected def indexGet(key: K): Set[String] =
        indexEntityProducer(key).ids

    override protected def clearIndex = {
        index.clear
        invertedIndex.clear
    }

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
            val entry = index.getOrElseUpdate(key, indexEntityProducer(key))
            entry.ids += entity.id
            invertedIndex.put(entity.id, entry)
        }

    private def deleteEntities(entities: List[Entity]) =
        for (entity <- entities) {
            val id = entity.id
            invertedIndex.get(id).map {
                entry =>
                    entry.ids -= id
                    invertedIndex.remove(id)
            }
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
