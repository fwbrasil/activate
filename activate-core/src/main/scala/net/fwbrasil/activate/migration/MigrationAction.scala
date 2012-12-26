package net.fwbrasil.activate.migration

import language.existentials
import net.fwbrasil.activate.entity.EntityValue

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
	val number: Int
	private[activate] def revertAction: StorageAction
}

case class CreateTable(
	migration: Migration,
	number: Int,
	tableName: String,
	columns: List[Column[_]])
		extends StorageAction
		with IfNotExists[CreateTable] {
	private[activate] def revertAction = {
		val revertAction =
			RemoveTable(migration, number, tableName)
		if (onlyIfNotExists)
			revertAction.ifExists
		revertAction
	}
}

case class CreateListTable(
	migration: Migration,
	number: Int,
	ownerTableName: String,
	listName: String,
	valueColumn: Column[_])
		extends StorageAction
		with IfNotExists[CreateListTable] {
	private[activate] def revertAction =
		RemoveListTable(migration, number, ownerTableName, listName)
}

case class RemoveListTable(
	migration: Migration,
	number: Int,
	ownerTableName: String,
	listName: String)
		extends StorageAction
		with IfExists[RemoveListTable]
		with Cascade {
	private[activate] def revertAction =
		throw CannotRevertMigration(this)
}

case class RenameTable(
	migration: Migration,
	number: Int,
	oldName: String,
	newName: String)
		extends StorageAction
		with IfExists[RenameTable] {
	private[activate] def revertAction = {
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
	private[activate] def revertAction =
		throw CannotRevertMigration(this)
}
case class AddColumn(
	migration: Migration,
	number: Int,
	tableName: String,
	column: Column[_])
		extends StorageAction
		with IfNotExists[AddColumn] {
	private[activate] def revertAction = {
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
	private[activate] def revertAction = {
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
	private[activate] def revertAction =
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
	private[activate] def revertAction = {
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
	private[activate] def revertAction = {
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
	private[activate] def revertAction = {
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
	private[activate] def revertAction = {
		val revertAction =
			AddReference(migration, number, tableName, columnName, referencedTable, constraintName)
		if (onlyIfExists)
			revertAction.ifNotExists
		revertAction
	}

}

case class CannotRevertMigration(action: MigrationAction) extends Exception
