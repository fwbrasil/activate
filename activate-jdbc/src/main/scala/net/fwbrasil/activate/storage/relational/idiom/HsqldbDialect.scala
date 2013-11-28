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
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.storage.marshalling.StorageModifyColumnType

object hsqldbDialect extends hsqldbDialect(pEscape = string => "\"" + string + "\"", pNormalize = string => string.toUpperCase) {
    def apply(escape: String => String = string => "\"" + string + "\"", normalize: String => String = string => string.toUpperCase()) = 
        new postgresqlDialect(escape, normalize)
}

class hsqldbDialect(pEscape: String => String, pNormalize: String => String) extends SqlIdiom {
    
    override def escape(string: String) =
        pEscape(pNormalize(string))

    def toSqlDmlRegexp(value: String, regex: String) =
        "REGEXP_MATCHES(" + value + ", " + regex + ")"

    override def findTableStatement(tableName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.TABLES " +
            " WHERE TABLE_SCHEMA = CURRENT_SCHEMA" +
            "   AND TABLE_NAME = '" + pNormalize(tableName) + "'"

    override def findTableColumnStatement(tableName: String, columnName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.COLUMNS " +
            " WHERE TABLE_SCHEMA = CURRENT_SCHEMA " +
            "   AND TABLE_NAME = '" + pNormalize(tableName) + "'" +
            "   AND COLUMN_NAME = '" + pNormalize(columnName) + "'"

    override def findIndexStatement(tableName: String, indexName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.SYSTEM_INDEXINFO " +
            " WHERE TABLE_SCHEM = CURRENT_SCHEMA " +
            "   AND TABLE_NAME = '" + pNormalize(tableName) + "'" +
            "   AND INDEX_NAME = '" + pNormalize(indexName) + "'"

    override def findConstraintStatement(tableName: String, constraintName: String): String =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS " +
            " WHERE CONSTRAINT_SCHEMA = CURRENT_SCHEMA " +
            "   AND CONSTRAINT_NAME = '" + pNormalize(constraintName) + "'"

    override def toSqlDdl(action: ModifyStorageAction): String = {
        action match {
            case StorageRemoveListTable(listTableName, ifNotExists) =>
                "DROP TABLE " + escape(listTableName)
            case StorageCreateListTable(ownerTableName, ownerIdColumn, listTableName, valueColumn, orderColumn, ifNotExists) =>
                "CREATE TABLE " + escape(listTableName) + "(\n" +
                    "	" + escape("owner") + " " + columnType(ownerIdColumn) + " REFERENCES " + escape(ownerTableName) + "(ID),\n" +
                    toSqlDdl(valueColumn) + ", " + toSqlDdl(orderColumn) +
                    ")"
            case StorageCreateTable(tableName, idColumn, columns, ifNotExists) =>
                "CREATE TABLE " + escape(tableName) + "(\n" +
                    "	ID " + columnType(idColumn) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
                    columns.map(toSqlDdl).mkString(", \n") +
                    ")"
            case StorageRenameTable(oldName, newName, ifExists) =>
                "ALTER TABLE " + escape(oldName) + " RENAME TO " + escape(newName)
            case StorageRemoveTable(name, ifExists, isCascade) =>
                "DROP TABLE " + escape(name) + (if (isCascade) " CASCADE" else "")
            case StorageAddColumn(tableName, column, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
            case StorageRenameColumn(tableName, oldName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(oldName) + " RENAME TO " + escape(column.name)
            case StorageRemoveColumn(tableName, name, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
            case StorageModifyColumnType(tableName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(column.name) + " SET DATA TYPE " + columnType(column)
            case StorageAddIndex(tableName, columns, indexName, ifNotExists, unique) =>
                "CREATE " + (if (unique) "UNIQUE " else "") + "INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + columns.map(escape).mkString(",") + ")"
            case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
                "DROP INDEX " + escape(name)
            case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
            case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP CONSTRAINT " + escape(constraintName)
        }
    }

    def concat(strings: String*) =
        "CONCAT(" + strings.mkString(", ") + ")"

    override def toSqlDdl(storageValue: StorageValue): String =
        storageValue match {
            case value: IntStorageValue =>
                "INTEGER"
            case value: LongStorageValue =>
                "BIGINT"
            case value: BooleanStorageValue =>
                "BOOLEAN"
            case value: StringStorageValue =>
                "VARCHAR(1000)"
            case value: FloatStorageValue =>
                "DOUBLE"
            case value: DateStorageValue =>
                "TIMESTAMP"
            case value: DoubleStorageValue =>
                "DOUBLE"
            case value: BigDecimalStorageValue =>
                "DECIMAL"
            case value: ListStorageValue =>
                "INTEGER"
            case value: ByteArrayStorageValue =>
                "BLOB"
            case value: ReferenceStorageValue =>
                "VARCHAR(45)"
        }
}

