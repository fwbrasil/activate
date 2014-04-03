# Migration

Migration is the storage schema evolution mechanism provided by Activate. Example:

``` scala
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

**Important**: The context definition must be declared in a base package of the migrations packages. Example: com.app.myContext for com.app.model.MyMigration

Class explanation

- **def timestamp: Long**

    Determines the execution order of the migration. If there is more than one migration with the same timestamp, an error will be thrown. A good pattern to this number is YYYYMMDDHHSS.

- **def up: Unit**

    Method to register the migration actions.

There is also a “**down**” method. It has a default implementation that reverts the migration when it’s possible. To define a custom down method, just override it.

``` scala
override def down = {
    table[MyEntity]
        .removeTable
}
```

When the default “down” method implementation can’t revert a migration, it throws a **CannotRevertMigration**. Migrations actions that can’t be automatically reverted:

- **Remove column**
- **Remove table**
- **Remove nested list table**
- **Custom script**

## Schema update / revert

On context startup, Activate automatically verifies if the storage schema must be updated and run pending migrations.

To override this default behavior add this code to your context:

``` scala
override protected lazy val runMigrationAtStartup = false
```

Methods to update/revert storage migrations available at the net.fwbrasil.activate.migration.Migration object:

- **def update(context: ActivateContext): Unit**

    Updates the storage to the last available migration.

- **def updateTo(context: ActivateContext, timestamp: Long): Unit** 

    Updates the storage to the specified timestamp.

- **def revertTo(context: ActivateContext, timestamp: Long): Unit**

    Reverts the storage to de specified timestamp.

## Manual migration

Manual migration is an especial type wich indicates that the migration runs only manually and doesn’t have an automatic timestamp execution control logic. Example:

``` scala
class ExampleMigration extends ManualMigration {
    def up = {
        table[MyEntity]
            .removeTable
    }
}
```

Methods to run manual migrations available at the net.fwbrasil.activate.migration.Migration object:

- **def execute(context: ActivateContext, manualMigration: ManualMigration): Unit**

    Executes migration up actions.

- **def revert(context: ActivateContext, manualMigration: ManualMigration): Unit**
    
    Executes migration down actions.

## Table

The Table class has methods to manipulate the schema. There are two methods to obtain a table object:

``` scala
table("MyEntity")
table[MyEntity]
```

The first uses the entity name and the second the entity class. It’s recommended using the second one. The first one is designed to be used when an entity class is deleted or renamed.

## Create table

Table instance method: 

**def createTable(definitions: ((ColumnDef) => Unit)*): CreateTable**

Example including the “ifNotExists” option:

``` scala
table[MyEntity]
    .createTable(
        _.column[String]("myString"),
        _.customColumn[Int]("myBigString", "CLOB"))
    .ifNotExists
```

## Remove table

Table instance method:

**def removeTable: RemoveTable**

Example including the “ifExists” and “cascade” options:

``` scala
table[MyEntity]
    .removeTable
    .ifExists
    .cascade
```

## Rename table

Table instance method:

**def renameTable(newName: String): RenameTable**

Example including the “ifExists” option:

``` scala
table("MyOldEntity")
    .renameTable("MyNewEntity")
    .ifExists
```

## Add column

Table instance method:

**def addColumn(definition: (ColumnDef) => Unit): AddColumn**

Example including the “ifNotExists” option:

``` scala
table[MyEntity]
    .addColumn(_.column[String]("myNewColumn"))
    .ifNotExists
```

## Rename column

Table instance method:

**def renameColumn(oldName: String, newColumn: (ColumnDef) => Unit): RenameColumn**

Example including the “ifExists” option:

``` scala
table[MyEntity]
    .renameColumn("oldName", _.column[String]("newName"))
    .ifExists
```

## Rename column

Table instance method:

**def renameColumn(oldName: String, newColumn: (ColumnDef) => Unit): RenameColumn**

Example including the “ifExists” option:

``` scala
table[MyEntity]
    .renameColumn("oldName", _.column[String]("newName"))
    .ifExists
```

## Modify column type

Table instance method:

**def modifyColumnType(column: (ColumnDef) => Unit): ModifyColumnType**

Example including the “ifExists” option:

``` scala
table[MyEntity]
    .modifyColumnType(_.column[String]("someColumn"))
    .ifExists
```

It is also possible to use a custom column type:

``` scala
table[MyEntity]
    .modifyColumnType(_.customColumn[String]("someColumn", "TEXT"))
    .ifExists
```

## Add index

Table instance method:

**def addIndex(columnName: String, indexName: String): AddIndex**

Example including the “ifNotExists” option:

``` scala
table[MyEntity]
    .addIndex("columnName", "indexName")
    .ifNotExists
```

## Remove index

Table instance method:

**def removeIndex(columnName: String, indexName: String): RemoveIndex**

Example including the “ifExists” option:

``` scala
table[MyEntity]
    .removeIndex("columnName", "indexName")
    .ifExists
```
## Add reference (FK)

Table instance method:

**def addReference(columnName: String, referencedTable: Table, constraintName: String): AddReference**

Example including the “ifNotExists” option:

``` scala
table[MyEntity]
    .addReference("columnName", table[ReferencedEntity], "constraintName")
    .ifNotExists
```

## Remove reference (FK)

Table instance method:

**def removeReference(columnName: String, referencedTable: Table, constraintName: String): RemoveReference**

Example including the “ifExists” option:

``` scala
table[MyEntity]
    .removeReference("columnName", table[ReferencedEntity], "constraintName")
    .ifNotExists
```

## Add nested list table

Table instance method:

**def createNestedListTableOf[T](listName: String, listTableName : String): IfNotExistsBag**

Creates a nested table to persist entity list properties. The “T” generic is the list content type.

``` scala
table[MyEntity]
    .createNestedListTableOf[Int]("intList", "listTableName")
    .ifNotExists
```

Mongo and memory storages persists simple “native” nested lists, ignoring this action. On jdbc this method perform this actions:

- Creates a column with the list name in the entity table. This column is used to indicate if the property is null (0) or not null (1).
- Creates a table with the listTableName. It contains two columns: “owner”, referencing the entity table, and “value”, containing the list item value.
- Creates an index on the owner column

Activate automatically manages the list content using this jdbc storage structures when the entity is created, modified or deleted.

## Remove nested list table

Table instance method:

**def removeNestedListTable(listName: String): IfExistsBag**

Removes a nested list table.

``` scala
table[MyEntity]
    .removeNestedListTable("intList")
    .ifExists
```

## Custom script

A custom script is an arbitrary code block that runs as a migration action inside a transaction. Example:

``` scala
customScript {
    all[MyEntity].foreach(_.doSomething)
}
```

Using mass update/delete:

``` scala
customScript {
    update {
        (entity: MyEntity) => where(entity.name :== "someName") set(entity.name := "someName2")
    }
    delete {
        (entity: MyEntity) => where(entity.name :== "someName2")
    }
}
```

It’s possible to use the direct access to the storage. Be aware that the direct access turns your software storage dependent. Example with a jdbc storage:

``` scala
customScript {
    val connection = storage.directAccess
    try {
        connection
            .prepareStatement("update MyEntity set attribute1 = 'a'")
            .executeUpdate
        connection.commit
    } catch {
        case e =>
            connection.rollback
            throw e
    } finally
        connection.close
}
```

## Custom column type

Activate determines the column type based on the attribute class. It’s possible to determine a custom column type. In the places that you use:

``` scala
_.column[String]("attribute1")
```

Just modify to:

``` scala
_.customColumn[String]("attribute1", "CLOB")
```

The second parameter is the database type name. Be aware that this makes your code storage dependent.

## Auxiliary methods

There are some auxiliary methods that can be used to modify the schema based on your entities classes automatically. Use these methods carefully. Otherwise, your migrations can be incompatible if you have to migrate a new database from zero.

They are self explanatory:

``` scala
createTableForAllEntities
    .ifNotExists
createTableForEntity[MyEntity]
    .ifNotExists
 
removeAllEntitiesTables
    .ifExists
    .cascade
 
createInexistentColumnsForAllEntities
createInexistentColumnsForEntity[MyEntity]
 
createReferencesForAllEntities
    .ifNotExists
createReferencesForEntity[MyEntity]
    .ifNotExists
 
removeReferencesForAllEntities
    .ifExists
```

[< Query]("/docs/query") | [Multiple VMs >]("/docs/multiple-vms")
