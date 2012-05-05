package net.fwbrasil.activate
import net.fwbrasil.activate.storage.memory.MemoryStorage
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.storage.mongo.MongoStorage
import net.fwbrasil.activate.storage.prevayler.PrevaylerMemoryStorage

class Teste extends Entity

object PolyglorPersistenceTestContext extends PolyglotActivateContext {

	val defaultStorage = new MongoStorage {
		override val host = "localhost"
		override val port = 27017
		override val db = "ACTIVATE_TEST"
	}

	val prevaylerStorage =
		new PrevaylerMemoryStorage

	entity[Teste](prevaylerStorage)

	def contextName = "PolyglorPersistenceTestContext"

}

class PolyglotPersistenceSpecs {

}