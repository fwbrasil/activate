package net.fwbrasil.activate.util

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class ConcurrentCache[V](name: String, defaultLimit: Int) {

    private val queue = new ConcurrentLinkedQueue[V]
    private val size = new AtomicInteger(0)
    private val limit = Option(System.getProperty(s"$name.limit")).map(_.toInt).getOrElse(defaultLimit)

    def poll = {
        size.decrementAndGet
        queue.poll
    }

    def offer(value: V) = {
        if (size.get < limit) {
            size.incrementAndGet
            queue.offer(value)
        }
    }

}