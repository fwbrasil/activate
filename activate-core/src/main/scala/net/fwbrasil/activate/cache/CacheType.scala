package net.fwbrasil.activate.cache

import com.google.common.collect.MapMaker
import com.google.common.cache.CacheBuilder

object CacheType extends Enumeration {
    abstract class CacheType(name: String) extends Val(name) {
        def mapMaker: MapMaker
        def cacheBuilder: CacheBuilder[Object, Object]
    }
    val hardReferences =
        new CacheType("hardReferences") {
            def mapMaker = (new MapMaker)
            def cacheBuilder = CacheBuilder.newBuilder
        }
    val softReferences =
        new CacheType("softReferences") {
            def mapMaker = (new MapMaker).softValues()
            def cacheBuilder = CacheBuilder.newBuilder.softValues
        }
    val weakReferences =
        new CacheType("weakReferences") {
            def mapMaker = (new MapMaker).weakValues()
            def cacheBuilder = CacheBuilder.newBuilder.weakValues
        }

}