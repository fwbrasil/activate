package net.fwbrasil.activate.index

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity._
import scala.collection.immutable.HashSet
import scala.collection.mutable.HashMap
import grizzled.slf4j.Logging
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.scala.UnsafeLazy._

class PersistedIndex[E <: Entity: Manifest, T] private[index] (
    keyProducer: E => T, context: ActivateContext)
        extends ActivateIndex[E, T](keyProducer, context)
        with Logging
        with Lockable {

    import context._

//    private val index = new HashMap[T, PersistedIndexEntry[T]]()
//    private val invertedIndex = new HashMap[String, PersistedIndexEntry[T]]()

    override protected def reload: Unit = ???
    override protected def indexGet(key: T): Set[String] = ???
    override protected def clearIndex: Unit = ???
    override protected def updateIndex(
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity]) = ???

}

//case class PersistedIndexEntry[T](key: T)
//        extends Entity {
//    var ids = HashSet[String]()
//}