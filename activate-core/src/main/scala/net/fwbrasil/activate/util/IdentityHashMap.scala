package net.fwbrasil.activate.util

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.{ MapLike, MapBuilder, HashMap }

final class IdentityHashMap[A <: AnyRef, B]() extends HashMap[A, B] with MapLike[A, B, IdentityHashMap[A, B]] {
    override protected def elemEquals(key1: A, key2: A): Boolean = key1 eq key2

    override protected def elemHashCode(key: A) = System.identityHashCode(key)

    override def empty: IdentityHashMap[A, B] = IdentityHashMap.empty

    // WTF: resize is private in HashTable!!
    //override def sizeHint(size: Int): Unit = resize(size)
}

object IdentityHashMap {
    type Coll = IdentityHashMap[_, _]

    implicit def canBuildFrom[A <: AnyRef, B] = new CanBuildFrom[Coll, (A, B), IdentityHashMap[A, B]] {
        def apply() = newBuilder[A, B]
        def apply(from: Coll) = {
            val builder = newBuilder[A, B]
            builder.sizeHint(from.size)
            builder
        }
    }

    def empty[A <: AnyRef, B]: IdentityHashMap[A, B] = new IdentityHashMap[A, B]

    def newBuilder[A <: AnyRef, B] = new MapBuilder[A, B, IdentityHashMap[A, B]](empty[A, B]) {
        override def +=(x: (A, B)): this.type = {
            elems += x
            this
        }
        override def sizeHint(size: Int): Unit = elems.sizeHint(size)
    }

    def apply[A <: AnyRef, B](elems: (A, B)*) = (newBuilder[A, B] ++= elems).result()
}