package net.fwbrasil.activate.util

import scala.util.Random
import java.lang.Comparable

case class RichList[T: Manifest](list: List[T]) {
	def sortComparable[V](f: (T) => Comparable[V]) =
		list.sort((a: T, b: T) => f(a).compareTo(f(b).asInstanceOf[V]) < 0)
	def sortIfComparable =
		if(classOf[Comparable[_]].isAssignableFrom(manifest[T].erasure))
			sortComparable[Any]((v: T) => v.asInstanceOf[Comparable[Any]])
	def randomElementOption =
		if(list.nonEmpty)
			Option(list(Random.nextInt(list.size)))
		else None
}

object RichList {
	implicit def toRichList[T: Manifest](list: List[T]) = RichList(list)
}

