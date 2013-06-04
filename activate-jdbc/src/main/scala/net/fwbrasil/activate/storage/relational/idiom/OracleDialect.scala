package net.fwbrasil.activate.storage.relational.idiom

import scala.collection.mutable.{ Map => MutableMap }
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
import java.sql.PreparedStatement
import java.sql.Types
import java.sql.ResultSet
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import java.sql.Connection
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery

object oracleDialect extends SqlIdiom {
    def toSqlDmlRegexp(value: String, regex: String) =
        "REGEXP_LIKE(" + value + ", " + regex + ")"

    override def findTableStatement(tableName: String) =
        "SELECT COUNT(1) " +
            "  FROM USER_TABLES " +
            " WHERE TABLE_NAME = '" + normalize(tableName) + "'"

    override def findTableColumnStatement(tableName: String, columnName: String) =
        "SELECT COUNT(1) " +
            "  FROM USER_TAB_COLUMNS " +
            " WHERE TABLE_NAME = '" + normalize(tableName) + "' " +
            "   AND COLUMN_NAME = '" + normalize(columnName) + "'"

    override def findIndexStatement(tableName: String, indexName: String) =
        "SELECT COUNT(1) " +
            "  FROM USER_INDEXES " +
            " WHERE INDEX_NAME = '" + normalize(indexName) + "'"

    override def findConstraintStatement(tableName: String, constraintName: String): String =
        "SELECT COUNT(1) " +
            "  FROM USER_CONSTRAINTS " +
            " WHERE TABLE_NAME = '" + normalize(tableName) + "'" +
            "   AND CONSTRAINT_NAME = '" + normalize(constraintName) + "'"

    def normalize(string: String) =
        string.toUpperCase.substring((string.length - 30).max(0), string.length)

    override def escape(string: String) =
        "\"" + normalize(string) + "\""

    override def toSqlDmlLimit(query: LimitedOrderedQuery[_]): String =
        ""

    override def toSqlDmlOrderBy(query: Query[_])(implicit binds: MutableMap[StorageValue, String]): String = {
        val fromSuper = super.toSqlDmlOrderBy(query)
        if (fromSuper.nonEmpty)
            fromSuper + " NULLS FIRST "
        else
            fromSuper
    }

    override def toSqlDmlQueryString(query: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit binds: MutableMap[StorageValue, String]) =
        query match {
            case query: LimitedOrderedQuery[_] =>
                "SELECT * FROM (" + super.toSqlDmlQueryString(query, entitiesReadFromCache) + ") WHERE ROWNUM<=" + query.limit
            case query =>
                super.toSqlDmlQueryString(query, entitiesReadFromCache)
        }

    override def toSqlDdl(action: ModifyStorageAction): String = {
        action match {
            case StorageRemoveListTable(ownerTableName, listName, ifNotExists) =>
                "DROP TABLE " + escape(ownerTableName + listName.capitalize)
            case StorageCreateListTable(ownerTableName, listName, valueColumn, orderColumn, ifNotExists) =>
                "CREATE TABLE " + escape(ownerTableName + listName.capitalize) + "(\n" +
                    "	" + escape("owner") + " " + toSqlDdl(ReferenceStorageValue(None)) + " REFERENCES " + escape(ownerTableName) + "(ID),\n" +
                    toSqlDdl(valueColumn) + ", " + toSqlDdl(orderColumn) +
                    ")"
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
            case StorageAddIndex(tableName, columnName, indexName, ifNotExists, unique) =>
                "CREATE " + (if (unique) "UNIQUE " else "") + "INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + escape(columnName) + ")"
            case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
                "DROP INDEX " + escape(name)
            case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
            case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP CONSTRAINT " + escape(constraintName)
        }
    }

    def concat(strings: String*) =
        strings.mkString(" || ")

    val emptyString = "" + '\u0000'

    override def setValue(ps: PreparedStatement, i: Int, storageValue: StorageValue): Unit = {
        storageValue match {
            case value: StringStorageValue =>
                setValue(ps, (v: String) => if (v == "") ps.setString(i, emptyString) else ps.setString(i, v), i, value.value, Types.VARCHAR)
            case other =>
                super.setValue(ps, i, other)
        }
    }

    override def getValue(resultSet: ActivateResultSet, i: Int, storageValue: StorageValue): StorageValue = {
        storageValue match {
            case value: StringStorageValue =>
                val value = resultSet.getString(i).map(string => if (string == emptyString) "" else string)
                StringStorageValue(value)
            case other =>
                super.getValue(resultSet, i, storageValue)
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
            case value: ListStorageValue =>
                "INTEGER"
            case value: ByteArrayStorageValue =>
                "BLOB"
            case value: ReferenceStorageValue =>
                "VARCHAR2(45)"
        }

}
