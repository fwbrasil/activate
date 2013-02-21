package net.fwbrasil.activate.migration

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.coordinator.Coordinator

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
            if (!coordinatorClientOption.isDefined || Coordinator.isServerVM)
                runMigration
            else
                warn("Migrations will not run. If there is a coordinator, only the coordinator instance can run migrations.")

}