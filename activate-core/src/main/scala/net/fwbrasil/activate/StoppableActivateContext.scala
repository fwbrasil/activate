package net.fwbrasil.activate

import net.fwbrasil.activate.migration.Migration

trait StoppableActivateContext extends ActivateContext {

	var running = false

	def start = synchronized {
		ActivateContext.clearContextCache
		running = true
		Migration.update(this)
	}
	def stop = synchronized {
		running = false
	}

	override protected lazy val runMigrationAtStartup = false
	override def acceptEntity[E <: Entity](entityClass: Class[E]) =
		running

}