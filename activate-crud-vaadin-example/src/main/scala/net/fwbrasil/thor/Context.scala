package net.fwbrasil.thor

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.memory.MemoryStorage
import net.fwbrasil.activate.storage.mongo.MongoStorage

object thorContext extends ActivateContext {
	//	val storage = new MemoryStorage
	val storage = new MongoStorage {
		override val host = "localhost"
		override val port = 27017
		override val db = "thor"
	}
	def contextName = "thorContext"
}