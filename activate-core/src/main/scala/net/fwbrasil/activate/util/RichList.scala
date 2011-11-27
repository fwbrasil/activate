package net.fwbrasil.activate.util

import scala.util.Random
import java.lang.Comparable

case class RichList[T: Manifest](list: List[T]) {
	def sortComparable[V](f: (T) => Comparable[V]) =
		list.sortWith((a: T, b: T) => f(a).compareTo(f(b).asInstanceOf[V]) < 0)
	def sortIfComparable =
		if(classOf[Comparable[_]].isAssignableFrom(manifest[T].erasure))
			sortComparable[Any]((v: T) => v.asInstanceOf[Comparable[Any]])
	def randomElementOption =
		if(list.nonEmpty)
			Option(list(Random.nextInt(list.size)))
		else None
	def onlyOne =
		if(list.size!=1)
			throw new IllegalStateException("List hasn't one element.")
		else
			list.head
}

object RichList {
	implicit def toRichList[T: Manifest](list: List[T]) = RichList(list)
	implicit def toRichList[T: Manifest](list: Seq[T]) = RichList(list.toList)
	
}