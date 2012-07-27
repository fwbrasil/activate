package net.fwbrasil.activate.migration

import net.fwbrasil.activate.entity.EntityValue

sealed trait MigrationAction {
	def revertAction: MigrationAction
	def migration: Migration
	def number: Int
	def hasToRun(from: (Long, Int), to: (Long, Int), isRevert: Boolean) = {
		if (!isRevert) {
			migration.timestamp > from._1 || (migration.timestamp == from._1 && number > from._2) &&
				migration.timestamp < to._1 || (migration.timestamp == to._1 && number <= to._2)
		} else {
			migration.timestamp > from._1 &&
				migration.timestamp <= to._1
		}
	}
	def isAfter(version: StorageVersion, isRevert: Boolean) =
		migration.timestamp > version.lastScript || (!isRevert && migration.timestamp == version.lastScript && number > version.lastAction)
	def isBefore(version: StorageVersion, isRevert: Boolean) =
		migration.timestamp < version.lastScript || (!isRevert && migration.timestamp == version.lastScript && number <= version.lastAction)
}

case class CustomScriptAction(migration: Migration, number: Int, f: () => Unit) extends MigrationAction {
	def revertAction =
		throw CannotRevertMigration(this)
}

sealed trait StorageAction extends MigrationAction {
	val migration: Migration
	val number: Int
	def revertAction: StorageAction
}

case class CreateTable(
	migration: Migration,
	number: Int, tableName: String,
	columns: List[Column[_]])
		extends StorageAction
		with IfNotExists[CreateTable] {
	def revertAction =
		RemoveTable(migration, number, tableName)
}
case class RenameTable(
	migration: Migration,
	number: Int,
	oldName: String,
	newName: String)
		extends StorageAction
		with IfExists[RenameTable] {
	def revertAction =
		RenameTable(migration, number, newName, oldName)
}
case class RemoveTable(
	migration: Migration,
	number: Int,
	name: String)
		extends StorageAction
		with IfExists[RemoveTable]
		with Cascade {
	def revertAction =
		throw CannotRevertMigration(this)
}
case class AddColumn(
	migration: Migration,
	number: Int,
	tableName: String,
	column: Column[_])
		extends StorageAction
		with IfNotExists[AddColumn] {
	def revertAction =
		RemoveColumn(migration, number, tableName, column.name)
}
case class RenameColumn(
	migration: Migration,
	number: Int,
	tableName: String,
	oldName: String,
	column: Column[_])
		extends StorageAction
		with IfExists[RenameColumn] {
	def revertAction =
		RenameColumn(
			migration,
			number,
			tableName,
			column.name,
			Column[Any](oldName, column.specificTypeOption)(
				column.m.asInstanceOf[Manifest[Any]],
				column.tval.asInstanceOf[Any => EntityValue[Any]]))
}
case class RemoveColumn(
	migration: Migration,
	number: Int,
	tableName: String,
	name: String)
		extends StorageAction
		with IfExists[RemoveColumn] {
	def revertAction =
		throw CannotRevertMigration(this)
}
case class AddIndex(
	migration: Migration,
	number: Int,
	tableName: String,
	columnName: String,
	indexName: String)
		extends StorageAction
		with IfNotExists[AddIndex] {
	def revertAction =
		RemoveIndex(
			migration,
			number,
			tableName,
			columnName,
			indexName)
}
case class RemoveIndex(
	migration: Migration,
	number: Int,
	tableName: String,
	columnName: String,
	name: String)
		extends StorageAction
		with IfExists[RemoveIndex] {
	def revertAction =
		throw CannotRevertMigration(this)
}
case class AddReference(
	migration: Migration,
	number: Int,
	tableName: String,
	columnName: String,
	referencedTable: String,
	constraintName: String)
		extends StorageAction
		with IfNotExists[AddReference] {
	def revertAction =
		RemoveReference(migration, number, tableName, columnName, referencedTable, constraintName)
}

case class RemoveReference(
	migration: Migration,
	number: Int,
	tableName: String,
	columnName: String,
	referencedTable: String,
	constraintName: String)
		extends StorageAction
		with IfExists[RemoveReference] {
	def revertAction =
		AddReference(migration, number, tableName, columnName, referencedTable, constraintName)
}

case class CannotRevertMigration(action: MigrationAction) extends Exception
