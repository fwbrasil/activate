package net.fwbrasil.activate.migration

import language.existentials
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.storage.Storage

sealed trait MigrationAction {
    private[activate] def revertAction: MigrationAction
    def migration: Migration
    def number: Int
    private[activate] def hasToRun(from: (Long, Int), to: (Long, Int), isRevert: Boolean) = {
        if (!isRevert) {
            migration.timestamp > from._1 || (migration.timestamp == from._1 && number > from._2) &&
                migration.timestamp < to._1 || (migration.timestamp == to._1 && number <= to._2)
        } else {
            migration.timestamp > from._1 &&
                migration.timestamp <= to._1
        }
    }
    private[activate] def isAfter(version: StorageVersion, isRevert: Boolean) =
        migration.timestamp > version.lastScript || (!isRevert && migration.timestamp == version.lastScript && number > version.lastAction)
    private[activate] def isBefore(version: StorageVersion, isRevert: Boolean) =
        migration.timestamp < version.lastScript || (!isRevert && migration.timestamp == version.lastScript && number <= version.lastAction)
}

case class CustomScriptAction(migration: Migration, number: Int, f: () => Unit) extends MigrationAction {
    private[activate] def revertAction =
        throw CannotRevertMigration(this)
}

sealed trait StorageAction extends MigrationAction {
    val migration: Migration
    val storage: Storage[_]
    val number: Int
    private[activate] def revertAction: StorageAction
}

case class CreateTable(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    idColumn: Column[_],
    columns: List[Column[_]])
        extends StorageAction
        with IfNotExists[CreateTable] {
    private[activate] def revertAction = {
        val revertAction =
            RemoveTable(migration, storage, number, tableName)
        if (onlyIfNotExists)
            revertAction.ifExists
        revertAction
    }
}

case class CreateListTable(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    ownerTableName: String,
    ownerIdColumn: Column[_],
    listTableName: String,
    valueColumn: Column[_])
        extends StorageAction
        with IfNotExists[CreateListTable] {
    private[activate] def revertAction =
        RemoveListTable(migration, storage, number, ownerTableName, listTableName)
}

case class RemoveListTable(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    ownerTableName: String,
    listTableName: String)
        extends StorageAction
        with IfExists[RemoveListTable]
        with Cascade {
    private[activate] def revertAction =
        throw CannotRevertMigration(this)
}

case class RenameTable(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    oldName: String,
    newName: String)
        extends StorageAction
        with IfExists[RenameTable] {
    private[activate] def revertAction = {
        val revertAction =
            RenameTable(migration, storage, number, newName, oldName)
        if (onlyIfExists)
            revertAction.ifExists
        revertAction
    }
}
case class RemoveTable(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    name: String)
        extends StorageAction
        with IfExists[RemoveTable]
        with Cascade {
    private[activate] def revertAction =
        throw CannotRevertMigration(this)
}
case class AddColumn(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    column: Column[_])
        extends StorageAction
        with IfNotExists[AddColumn] {
    private[activate] def revertAction = {
        val revertAction =
            RemoveColumn(migration, storage, number, tableName, column.name)
        if (onlyIfNotExists)
            revertAction.ifExists
        revertAction
    }

}
case class RenameColumn(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    oldName: String,
    column: Column[_])
        extends StorageAction
        with IfExists[RenameColumn] {
    private[activate] def revertAction = {
        val revertAction =
            RenameColumn(
                migration,
                storage,
                number,
                tableName,
                column.name,
                Column[Any](oldName, column.specificTypeOption)(
                    column.m.asInstanceOf[Manifest[Any]],
                    column.tval.asInstanceOf[Any => EntityValue[Any]]))
        if (onlyIfExists)
            revertAction.ifExists
        revertAction
    }

}
case class RemoveColumn(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    name: String)
        extends StorageAction
        with IfExists[RemoveColumn] {
    private[activate] def revertAction =
        throw CannotRevertMigration(this)
}
case class ModifyColumnType(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    column: Column[_])
        extends StorageAction
        with IfExists[ModifyColumnType] {
    private[activate] def revertAction =
        throw CannotRevertMigration(this)
}
case class AddIndex(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    columns: List[String],
    indexName: String,
    unique: Boolean)
        extends StorageAction
        with IfNotExists[AddIndex] {
    private[activate] def revertAction = {
        val revertAction =
            RemoveIndex(
                migration,
                storage,
                number,
                tableName,
                columns,
                indexName,
                unique)
        if (onlyIfNotExists)
            revertAction.ifExists
        revertAction
    }

}
case class RemoveIndex(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    columns: List[String],
    name: String,
    unique: Boolean)
        extends StorageAction
        with IfExists[RemoveIndex] {
    private[activate] def revertAction = {
        val revertAction =
            AddIndex(
                migration, storage,
                number,
                tableName,
                columns,
                name,
                unique)
        if (onlyIfExists)
            revertAction.ifNotExists
        revertAction
    }
}
case class AddReference(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    columnName: String,
    referencedTable: String,
    constraintName: String)
        extends StorageAction
        with IfNotExists[AddReference] {
    private[activate] def revertAction = {
        val revertAction =
            RemoveReference(
                migration,
                storage,
                number,
                tableName,
                columnName,
                referencedTable,
                constraintName)
        if (onlyIfNotExists)
            revertAction.ifExists
        revertAction
    }
}

case class RemoveReference(
    migration: Migration,
    storage: Storage[_],
    number: Int,
    tableName: String,
    columnName: String,
    referencedTable: String,
    constraintName: String)
        extends StorageAction
        with IfExists[RemoveReference] {
    private[activate] def revertAction = {
        val revertAction =
            AddReference(
                migration,
                storage,
                number,
                tableName,
                columnName,
                referencedTable,
                constraintName)
        if (onlyIfExists)
            revertAction.ifNotExists
        revertAction
    }

}

case class CannotRevertMigration(action: MigrationAction) extends Exception
