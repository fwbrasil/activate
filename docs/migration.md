# Migration

Migration is the storage schema evolution mechanism provided by Activate.

Example:

```scala
class ExampleMigration extends Migration {
    def timestamp = 201208191834l
    def up = {
        table[MyEntity]
            .createTable(
                _.column[String]("attribute1"),
                _.column[Int]("attribute2"))
    }
}
```

There is no need to register you migration. Just put it on the project classpath and it will be found by Activate.

> The context definition must be declared in a base package of the migrations packages. Example: com.app.myContext for com.app.model.MyMigration

## Class explanation

**def timestamp: Long**

Determines the execution order of the migration. If there is more than one migration with the same timestamp, an error will be thrown. A good pattern to this number is YYYYMMDDHHSS.

**def up: Unit**

Method to register the migration actions.

There is also a “down” method. It has a default implementation that reverts the migration when it’s possible. To define a custom down method, just override it.

``` scala
override def down = {
    table[MyEntity]
        .removeTable
}
```

When the default “down” method implementation can’t revert a migration, it throws a CannotRevertMigration. Migrations actions that can’t be automatically reverted:

- Remove column
- Remove table
- Remove nested list table
- Custom script

# Schema update / revert

On context startup, Activate automatically verifies if the storage schema must be updated and run pending migrations.

To override this default behavior add this code to your context:

``` scala
override protected lazy val runMigrationAtStartup = false
```

Methods to update/revert storage migrations available at the net.fwbrasil.activate.migration.Migration object:

`def update(context: ActivateContext): Unit` - Updates the storage to the last available migration.

`def updateTo(context: ActivateContext, timestamp: Long): Unit` - Updates the storage to the specified timestamp.

`def revertTo(context: ActivateContext, timestamp: Long): Unit` - Reverts the storage to de specified timestamp.
