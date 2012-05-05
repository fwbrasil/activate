package net.fwbrasil.activate.util

import scala.util.Random
import java.lang.Comparable
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.util.Reflection.toNiceObject

case class RichList[T: Manifest](iterable: Iterable[T]) {

	def list = iterable.toList

	def filterByType[A: Manifest, B](f: (T) => Class[_]): List[B] =
		iterable.filter((elem) => erasureOf[A].isAssignableFrom(f(elem))).toList.asInstanceOf[List[B]]

	def sortComparable[V](f: (T) => Comparable[V]): List[T] =
		list.sortWith((a: T, b: T) => f(a).compareTo(f(b).asInstanceOf[V]) < 0)

	def sortIfComparable: List[T] =
		if (classOf[Comparable[_]].isAssignableFrom(erasureOf[T]))
			sortComparable[Any]((v: T) => v.asInstanceOf[Comparable[Any]])
		else
			iterable.toList

	def randomElementOption =
		if (iterable.nonEmpty)
			Option(list(Random.nextInt(list.size)))
		else None

	def randomize =
		(for (elem <- iterable) yield (Random.nextInt, elem)).toList.sortBy(_._1).map(_._2)

	def onlyOne: T =
		onlyOne("List hasn't one element.")

	def onlyOne(msg: => String): T =
		if (iterable.size != 1)
			throw new IllegalStateException(msg)
		else
			iterable.head

	def emptyOrOne: Option[T] =
		emptyOrOne("List has more than one element")

	def emptyOrOne(msg: => String): Option[T] =
		if (iterable.size == 0)
			None
		else if (iterable.size == 1)
			Some(iterable.head)
		else throw new IllegalStateException(msg)

	def collect[R](func: (T) => R) =
		for (elem <- iterable)
			yield func(elem)

	def select(func: (T) => Boolean) =
		for (elem <- iterable; if (func(elem)))
			yield elem

	def mapBy[R](f: (T) => R): Map[R, T] =
		list.map((v) => (f(v), v)).toMap
}

object RichList {

	implicit def toRichList[T: Manifest](list: Iterable[T]): RichList[T] = RichList(list)

}