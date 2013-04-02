package net.fwbrasil.activate.migration

import net.fwbrasil.activate.ActivateContext

trait MigrationContext {
    this: ActivateContext =>

    type Migration = net.fwbrasil.activate.migration.Migration
    type ManualMigration = net.fwbrasil.activate.migration.ManualMigration

    protected[activate] def execute(action: StorageAction) =
        action.storage.migrate(action)

    protected[activate] def runMigration =
        Migration.update(this)

    def delayedInit(x: => Unit): Unit = {
        x
        runStartupMigration
    }

    protected val runMigrationAtStartup = true

    private[activate] def runStartupMigration =
        if (runMigrationAtStartup)
            runMigration
}