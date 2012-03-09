package net.fwbrasil.activate.util

import scala.collection.mutable.{ HashMap => MutableHashMap }
import scala.collection.mutable.SynchronizedMap

object CacheUtil {

	private val _cache = new MutableHashMap[CacheKey, Any]() with SynchronizedMap[CacheKey, Any]

	case class CacheKey(f: Any, key: Product)

	def cache[R](key: Object)(f: () => R): R =
		cache(Tuple1(key))(f)
	def cache[R](key: Product)(f: () => R): R =
		_cache.getOrElseUpdate(CacheKey(f.getClass, key), f().asInstanceOf[Any]).asInstanceOf[R]
}