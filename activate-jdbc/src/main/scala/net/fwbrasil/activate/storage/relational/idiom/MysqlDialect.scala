package net.fwbrasil.activate.storage.relational.idiom

import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import java.sql.ResultSet
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
import java.sql.PreparedStatement
import java.util.Date
import java.sql.Types
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import java.sql.Connection
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageModifyColumnType

object mySqlDialect extends mySqlDialect(pEscape = string => "`" + string + "`", pNormalize = string => string) {
    def apply(escape: String => String = string => "`" + string + "`", normalize: String => String = string => string) = 
        new postgresqlDialect(escape, normalize)
}

class mySqlDialect(pEscape: String => String, pNormalize: String => String) extends SqlIdiom {
    
    override def escape(string: String) =
        pEscape(pNormalize(string))

    override def getValue(resultSet: ActivateResultSet, i: Int, storageValue: StorageValue) = {
        storageValue match {
            case value: DateStorageValue =>
                DateStorageValue(resultSet.getLong(i).map((t: Long) => new Date(t)))
            case other =>
                super.getValue(resultSet, i, storageValue)
        }
    }

    override def setValue(ps: PreparedStatement, i: Int, storageValue: StorageValue): Unit = {
        storageValue match {
            case value: DateStorageValue =>
                setValue(ps, (v: Date) => ps.setLong(i, v.getTime), i, value.value, Types.BIGINT)
            case other =>
                super.setValue(ps, i, storageValue)
        }
    }

    def toSqlDmlRegexp(value: String, regex: String) =
        value + " REGEXP " + regex

    override def findTableStatement(tableName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.TABLES " +
            " WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
            "   AND TABLE_NAME = '" + pNormalize(tableName) + "'"

    override def findTableColumnStatement(tableName: String, columnName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.COLUMNS " +
            " WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
            "   AND TABLE_NAME = '" + pNormalize(tableName) + "'" +
            "   AND COLUMN_NAME = '" + pNormalize(columnName) + "'"

    override def findIndexStatement(tableName: String, indexName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.STATISTICS " +
            " WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
            "   AND TABLE_NAME = '" + pNormalize(tableName) + "'" +
            "   AND INDEX_NAME = '" + pNormalize(indexName) + "'"

    override def findConstraintStatement(tableName: String, constraintName: String): String =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
            " WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
            "   AND TABLE_NAME = '" + pNormalize(tableName) + "'" +
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
                    " " + escape("id") + " " + columnType(idColumn) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
                    columns.map(toSqlDdl).mkString(", \n") +
                    ")"
            case StorageRenameTable(oldName, newName, ifExists) =>
                "RENAME TABLE " + escape(oldName) + " TO " + escape(newName)
            case StorageRemoveTable(name, ifExists, isCascade) =>
                "DROP TABLE " + escape(name) + (if (isCascade) " CASCADE" else "")
            case StorageAddColumn(tableName, column, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
            case StorageRenameColumn(tableName, oldName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " CHANGE " + escape(oldName) + " " + toSqlDdl(column)
            case StorageModifyColumnType(tableName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " MODIFY COLUMN " + escape(column.name) + " " + columnType(column)
            case StorageRemoveColumn(tableName, name, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
            case StorageAddIndex(tableName, columns, indexName, ifNotExists, unique) =>
                "CREATE " + (if (unique) "UNIQUE " else "") + "INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + columns.map(escape).mkString(",") + ")"
            case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
                "DROP INDEX " + escape(name) + " ON " + escape(tableName)
            case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
            case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP FOREIGN KEY " + escape(constraintName)
        }
    }

    override def concat(strings: String*) =
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
                "VARCHAR(200)"
            case value: FloatStorageValue =>
                "DOUBLE"
            case value: DateStorageValue =>
                "LONG"
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

