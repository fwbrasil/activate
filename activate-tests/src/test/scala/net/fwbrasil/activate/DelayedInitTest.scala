package net.fwbrasil.activate

import net.fwbrasil.activate.util.uuid.Idable

class DelayedInitTest {

	def a = println("a")

	trait A extends DelayedInit with Idable {
		def delayedInit(body: => Unit) = {
			body
		}
	}

	class C extends A {
//		a
		println("a")
	}

}
