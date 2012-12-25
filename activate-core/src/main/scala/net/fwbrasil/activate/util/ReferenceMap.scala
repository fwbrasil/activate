package net.fwbrasil.activate.util

import org.apache.commons.collections.map.{ ReferenceMap => ReferenceMapWrapped }
import org.apache.commons.collections.map.AbstractReferenceMap
import scala.collection.generic._
import scala.collection.convert.Wrappers._

class ReferenceWeakMap[A, B] extends JMapWrapper[A, B](new ReferenceMapWrapped(AbstractReferenceMap.WEAK, AbstractReferenceMap.WEAK).asInstanceOf[java.util.Map[A, B]])
		with JMapWrapperLike[A, B, ReferenceWeakMap[A, B]] {
	override def empty = new ReferenceWeakMap[A, B]
}

object ReferenceWeakMap extends MutableMapFactory[ReferenceWeakMap] {
	implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), ReferenceWeakMap[A, B]] = new MapCanBuildFrom[A, B]
	def empty[A, B]: ReferenceWeakMap[A, B] = new ReferenceWeakMap[A, B]
}

class ReferenceWeakKeyMap[A, B] extends JMapWrapper[A, B](new ReferenceMapWrapped(AbstractReferenceMap.WEAK, AbstractReferenceMap.HARD).asInstanceOf[java.util.Map[A, B]])
		with JMapWrapperLike[A, B, ReferenceWeakKeyMap[A, B]] {
	override def empty = new ReferenceWeakKeyMap[A, B]
}

object ReferenceWeakKeyMap extends MutableMapFactory[ReferenceWeakKeyMap] {
	implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), ReferenceWeakKeyMap[A, B]] = new MapCanBuildFrom[A, B]
	def empty[A, B]: ReferenceWeakKeyMap[A, B] = new ReferenceWeakKeyMap[A, B]
}

class ReferenceWeakValueMap[A, B] extends JMapWrapper[A, B](new ReferenceMapWrapped(AbstractReferenceMap.HARD, AbstractReferenceMap.WEAK).asInstanceOf[java.util.Map[A, B]])
		with JMapWrapperLike[A, B, ReferenceWeakValueMap[A, B]] {
	override def empty = new ReferenceWeakValueMap[A, B]
}

object ReferenceWeakValueMap extends MutableMapFactory[ReferenceWeakValueMap] {
	implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), ReferenceWeakValueMap[A, B]] = new MapCanBuildFrom[A, B]
	def empty[A, B]: ReferenceWeakValueMap[A, B] = new ReferenceWeakValueMap[A, B]
}

// SOFT

class ReferenceSoftMap[A, B] extends JMapWrapper[A, B](new ReferenceMapWrapped(AbstractReferenceMap.SOFT, AbstractReferenceMap.SOFT).asInstanceOf[java.util.Map[A, B]])
		with JMapWrapperLike[A, B, ReferenceSoftMap[A, B]] {
	override def empty = new ReferenceSoftMap[A, B]
}

object ReferenceSoftMap extends MutableMapFactory[ReferenceSoftMap] {
	implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), ReferenceSoftMap[A, B]] = new MapCanBuildFrom[A, B]
	def empty[A, B]: ReferenceSoftMap[A, B] = new ReferenceSoftMap[A, B]
}

class ReferenceSoftKeyMap[A, B] extends JMapWrapper[A, B](new ReferenceMapWrapped(AbstractReferenceMap.SOFT, AbstractReferenceMap.HARD).asInstanceOf[java.util.Map[A, B]])
		with JMapWrapperLike[A, B, ReferenceSoftKeyMap[A, B]] {
	override def empty = new ReferenceSoftKeyMap[A, B]
}

object ReferenceSoftKeyMap extends MutableMapFactory[ReferenceSoftKeyMap] {
	implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), ReferenceSoftKeyMap[A, B]] = new MapCanBuildFrom[A, B]
	def empty[A, B]: ReferenceSoftKeyMap[A, B] = new ReferenceSoftKeyMap[A, B]
}

class ReferenceSoftValueMap[A, B] extends JMapWrapper[A, B](new ReferenceMapWrapped(AbstractReferenceMap.HARD, AbstractReferenceMap.SOFT).asInstanceOf[java.util.Map[A, B]])
		with JMapWrapperLike[A, B, ReferenceSoftValueMap[A, B]] {
	override def empty = new ReferenceSoftValueMap[A, B]
}

object ReferenceSoftValueMap extends MutableMapFactory[ReferenceSoftValueMap] {
	implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), ReferenceSoftValueMap[A, B]] = new MapCanBuildFrom[A, B]
	def empty[A, B]: ReferenceSoftValueMap[A, B] = new ReferenceSoftValueMap[A, B]
}

