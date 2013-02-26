package net.fwbrasil.activate

import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.entity.EntityHelper

trait StoppableActivateContext extends ActivateContext {

    var running = false

    def start = synchronized {
        ActivateContext.clearCaches()
        EntityHelper.initialize(this.getClass)
        running = true
        Migration.update(this)
    }
    def stop = synchronized {
        running = false
    }

    override protected val runMigrationAtStartup = false
    override def acceptEntity[E <: Entity](entityClass: Class[E]) =
        running

}