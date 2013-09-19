package net.fwbrasil.activate.util

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import com.google.common.cache.CacheBuilder
import com.google.common.cache.Cache

class ConcurrentMapCache[K, V](name: String, defaultLimit: Int) {

	private val limit = Option(System.getProperty(s"$name.limit")).map(_.toInt).getOrElse(defaultLimit)
    private val map = CacheBuilder.newBuilder.maximumSize(limit).build.asInstanceOf[Cache[K, V]]
	
    def get(key: K) = 
        map.get(key)
        
    def put(key: K, value: V) =
        map.put(key, value)

    def remove(key: K) =
        map.invalidate(key)

}