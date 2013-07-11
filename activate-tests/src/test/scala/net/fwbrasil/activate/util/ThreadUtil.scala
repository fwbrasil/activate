package net.fwbrasil.activate.util

import scala.collection.mutable.ListBuffer

object ThreadUtil {

    def runWithThreads[R](n: Int = 100)(f: => R) = {
        var res = ListBuffer[R]()
        val threads =
            for (i <- 0 until n) yield new Thread {
                override def run = {
                    val elem = f
                    res.synchronized(res += elem)
                }
            }
        threads.foreach(_.start)
        threads.foreach(_.join)
        require(res.size == n)
        res.toList
    }
}