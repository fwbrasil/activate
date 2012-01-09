package net.fwbrasil.activate.util

trait Initializable {

	var initialized = false
	def doInitialized[R](f: => R): R = {
		if (!initialized)
			this.synchronized {
				initialize
				initialized = true
			}
		f
	}
	def initialize
}