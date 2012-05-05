package net.fwbrasil.activate

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil._

trait PolyglotActivateContext extends ActivateContext {

	val defaultStorage: Storage
	val storage = defaultStorage
	private var aditionalStoragesEntities =
		Map[Storage, Class[_ <: Entity]]()
	def entity[E <: Entity: Manifest](storage: Storage) =
		aditionalStoragesEntities += storage -> erasureOf[E]

}