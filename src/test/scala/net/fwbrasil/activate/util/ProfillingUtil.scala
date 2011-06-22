package net.fwbrasil.activate.util

object ProfillingUtil {

	def profile[B](blockName: String = "")(f: => B): B = {
		println("Starting " + blockName)
		val start = System.currentTimeMillis
		val result = f
		val end = System.currentTimeMillis
		println("Ended " + blockName + "(" + (end-start) + "ms)")
		result
	}
}