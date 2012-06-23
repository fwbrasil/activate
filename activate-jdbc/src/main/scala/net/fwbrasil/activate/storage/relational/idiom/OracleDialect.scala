package net.fwbrasil.activate.storage.relational.idiom

import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
import net.fwbrasil.activate.storage.marshalling.StorageAddReference
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex

object oracleDialect extends SqlIdiom {
	def toSqlDmlRegexp(value: String, regex: String) =
		"REGEXP_LIKE(" + value + ", " + regex + ")"

	override def findTableStatement(tableName: String) =
		"SELECT COUNT(1) " +
			"  FROM USER_TABLES " +
			" WHERE TABLE_NAME = '" + tableName.toUpperCase + "'"

	override def findTableColumnStatement(tableName: String, columnName: String) =
		"SELECT COUNT(1) " +
			"  FROM USER_TAB_COLUMNS " +
			" WHERE TABLE_NAME = '" + tableName.toUpperCase + "' " +
			"   AND COLUMN_NAME = '" + columnName.toUpperCase + "'"

	override def findIndexStatement(tableName: String, indexName: String) =
		"SELECT COUNT(1) " +
			"  FROM USER_INDEXES " +
			" WHERE INDEX_NAME = '" + indexName.toUpperCase + "'"

	override def findConstraintStatement(tableName: String, constraintName: String): String =
		"SELECT COUNT(1) " +
			"  FROM USER_CONSTRAINTS " +
			" WHERE TABLE_NAME = '" + tableName + "'" +
			"   AND CONSTRAINT_NAME = '" + constraintName + "'"

	override def escape(string: String) =
		"\"" + string.toUpperCase.substring(0, string.length.min(30)) + "\""

	override def toSqlDdl(action: ModifyStorageAction): String = {
		action match {
			case StorageCreateTable(tableName, columns, ifNotExists) =>
				"CREATE TABLE " + escape(tableName) + "(\n" +
					"	ID " + toSqlDdl(ReferenceStorageValue(None)) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
					columns.map(toSqlDdl).mkString(", \n") +
					")"
			case StorageRenameTable(oldName, newName, ifExists) =>
				"ALTER TABLE " + escape(oldName) + " RENAME TO " + escape(newName)
			case StorageRemoveTable(name, ifExists, isCascade) =>
				"DROP TABLE " + escape(name) + (if (isCascade) " CASCADE constraints" else "")
			case StorageAddColumn(tableName, column, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
			case StorageRenameColumn(tableName, oldName, column, ifExists) =>
				"ALTER TABLE " + escape(tableName) + " RENAME COLUMN " + escape(oldName) + " TO " + escape(column.name)
			case StorageRemoveColumn(tableName, name, ifExists) =>
				"ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
			case StorageAddIndex(tableName, columnName, indexName, ifNotExists) =>
				"CREATE INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + escape(columnName) + ")"
			case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
				"DROP INDEX " + escape(name)
			case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
			case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " DROP CONSTRAINT " + escape(constraintName)
		}
	}

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
			case value: LongStorageValue =>
				"DECIMAL"
			case value: BooleanStorageValue =>
				"NUMBER(1)"
			case value: StringStorageValue =>
				"VARCHAR2(4000)"
			case value: FloatStorageValue =>
				"DOUBLE PRECISION"
			case value: DateStorageValue =>
				"TIMESTAMP"
			case value: DoubleStorageValue =>
				"DOUBLE PRECISION"
			case value: BigDecimalStorageValue =>
				"DECIMAL"
			case value: ByteArrayStorageValue =>
				"BLOB"
			case value: ReferenceStorageValue =>
				"VARCHAR2(45)"
		}

}
