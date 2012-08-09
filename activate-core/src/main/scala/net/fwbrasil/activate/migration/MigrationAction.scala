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
	def revertAction = {
		val revertAction =
			RemoveTable(migration, number, tableName)
		if (onlyIfNotExists)
			revertAction.ifExists
		revertAction
	}
}
case class RenameTable(
	migration: Migration,
	number: Int,
	oldName: String,
	newName: String)
		extends StorageAction
		with IfExists[RenameTable] {
	def revertAction = {
		val revertAction =
			RenameTable(migration, number, newName, oldName)
		if (onlyIfExists)
			revertAction.ifExists
		revertAction
	}
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
	def revertAction = {
		val revertAction =
			RemoveColumn(migration, number, tableName, column.name)
		if (onlyIfNotExists)
			revertAction.ifExists
		revertAction
	}

}
case class RenameColumn(
	migration: Migration,
	number: Int,
	tableName: String,
	oldName: String,
	column: Column[_])
		extends StorageAction
		with IfExists[RenameColumn] {
	def revertAction = {
		val revertAction =
			RenameColumn(
				migration,
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
	def revertAction = {
		val revertAction =
			RemoveIndex(
				migration,
				number,
				tableName,
				columnName,
				indexName)
		if (onlyIfNotExists)
			revertAction.ifExists
		revertAction
	}

}
case class RemoveIndex(
	migration: Migration,
	number: Int,
	tableName: String,
	columnName: String,
	name: String)
		extends StorageAction
		with IfExists[RemoveIndex] {
	def revertAction = {
		val revertAction =
			AddIndex(
				migration,
				number,
				tableName,
				columnName,
				name)
		if (onlyIfExists)
			revertAction.ifNotExists
		revertAction
	}
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
	def revertAction = {
		val revertAction =
			RemoveReference(migration, number, tableName, columnName, referencedTable, constraintName)
		if (onlyIfNotExists)
			revertAction.ifExists
		revertAction
	}
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
	def revertAction = {
		val revertAction =
			AddReference(migration, number, tableName, columnName, referencedTable, constraintName)
		if (onlyIfExists)
			revertAction.ifNotExists
		revertAction
	}

}

case class CannotRevertMigration(action: MigrationAction) extends Exception
