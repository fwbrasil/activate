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
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.storage.relational.SqlStatement
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import java.sql.PreparedStatement
import java.sql.Types
import java.sql.ResultSet
import java.sql.Connection
import net.fwbrasil.activate.statement.query.OrderByCriteria
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import java.util.Date

object db2Dialect extends SqlIdiom {

    def toSqlDmlRegexp(value: String, regex: String) =
        throw new UnsupportedOperationException("DB2 dialect does not support regexp operator.")

    override def findTableStatement(tableName: String) =
        "SELECT COUNT(1) " +
            "  FROM SYSCAT.TABLES " +
            " WHERE TABSCHEMA = CURRENT_SCHEMA " +
            "   AND TABNAME = '" + tableName.toUpperCase + "'"

    override def findTableColumnStatement(tableName: String, columnName: String) =
        "SELECT COUNT(1) " +
            "  FROM SYSCAT.COLUMNS " +
            " WHERE TABSCHEMA = CURRENT_SCHEMA " +
            "   AND TABNAME = '" + tableName.toUpperCase + "'" +
            "   AND COLNAME = '" + columnName.toUpperCase + "'"

    override def findIndexStatement(tableName: String, indexName: String) =
        "SELECT COUNT(1) " +
            "  FROM SYSCAT.INDEXES " +
            " WHERE TABSCHEMA = CURRENT_SCHEMA " +
            "   AND TABNAME = '" + tableName.toUpperCase + "'" +
            "   AND INDNAME = '" + indexName.toUpperCase + "'"

    override def findConstraintStatement(tableName: String, constraintName: String): String =
        "SELECT COUNT(1) " +
            "  FROM SYSCAT.TABCONST " +
            " WHERE TABSCHEMA = CURRENT_SCHEMA " +
            "   AND TABNAME = '" + tableName.toUpperCase + "'" +
            "   AND CONSTNAME = '" + constraintName.toUpperCase + "'"

    override def escape(string: String) =
        "\"" + string.toUpperCase + "\""

    override def toSqlDdlAction(action: ModifyStorageAction): List[SqlStatement] =
        action match {
            case removeColumn: StorageRemoveColumn =>
                val fromSuper = super.toSqlDdlAction(removeColumn).head
                List(
                    fromSuper,
                    new SqlStatement(
                        statement = "COMMIT"),
                    new SqlStatement(
                        statement = s"Call Sysproc.admin_cmd ('reorg Table ${removeColumn.tableName}')",
                        restrictionQuery = ifExistsRestriction(findTableStatement(removeColumn.tableName), true)))
            case other => super.toSqlDdlAction(other)
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
                    "	ID " + toSqlDdl(ReferenceStorageValue(None)) + " PRIMARY KEY NOT NULL" + (if (columns.nonEmpty) ",\n" else "") +
                    columns.map(toSqlDdl).mkString(", \n") +
                    ")"
            case StorageRenameTable(oldName, newName, ifExists) =>
                "RENAME TABLE " + escape(oldName) + " TO " + escape(newName)
            case StorageRemoveTable(name, ifExists, isCascade) =>
                "DROP TABLE " + escape(name)
            case StorageAddColumn(tableName, column, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
            case StorageRenameColumn(tableName, oldName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(oldName) + " RENAME TO " + escape(column.name)
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

    def concat(strings: String*) =
        strings.mkString(" || ")

    override def toSqlDml(criteria: OrderByCriteria[_])(implicit binds: MutableMap[StorageValue, String]): String =
        "COALESCE(" + toSqlDml(criteria.value) + "," + bind(firstValue(criteria.value)) + ") " + (
            if (criteria.direction ==
                orderByAscendingDirection)
                "asc"
            else
                "desc")

    private def firstValue(value: StatementSelectValue[_])(implicit binds: MutableMap[StorageValue, String]): StorageValue = {
        Marshaller.marshalling(value.entityValue) match {
            case value: IntStorageValue =>
                IntStorageValue(Some(Int.MinValue))
            case value: LongStorageValue =>
                LongStorageValue(Some(Long.MinValue))
            case value: BooleanStorageValue =>
                IntStorageValue(Some(Int.MinValue))
            case value: StringStorageValue =>
                StringStorageValue(Some(Char.MinValue.toString))
            case value: FloatStorageValue =>
                FloatStorageValue(Some(Float.MinValue))
            case value: DateStorageValue =>
                DateStorageValue(Some(new Date(0)))
            case value: DoubleStorageValue =>
                DoubleStorageValue(Some(Double.MinValue))
            case value: BigDecimalStorageValue =>
                BigDecimalStorageValue(Some(Long.MinValue))
            case value: ByteArrayStorageValue =>
                throw new UnsupportedOperationException("Order by a byte array.")
            case value: ListStorageValue =>
                IntStorageValue(Some(Int.MinValue))
            case value: ReferenceStorageValue =>
                IntStorageValue(Some(Int.MinValue))
        }
    }

    override def setValue(ps: PreparedStatement, i: Int, storageValue: StorageValue): Unit =
        storageValue match {
            case value: BooleanStorageValue =>
                setValue(ps, (v: Boolean) => ps.setInt(i, if (v) 1 else 0), i, value.value, Types.INTEGER)
            case other =>
                super.setValue(ps, i, storageValue)
        }

    override def getValue(resultSet: ResultSet, i: Int, storageValue: StorageValue, connection: Connection): StorageValue =
        storageValue match {
            case value: BooleanStorageValue =>
                BooleanStorageValue(getValue(resultSet, resultSet.getInt(i) == 1))
            case other =>
                super.getValue(resultSet, i, storageValue, connection)
        }

    override def toSqlDdl(storageValue: StorageValue): String =
        storageValue match {
            case value: IntStorageValue =>
                "INTEGER"
            case value: LongStorageValue =>
                "BIGINT"
            case value: BooleanStorageValue =>
                "SMALLINT"
            case value: StringStorageValue =>
                "VARCHAR(1000)"
            case value: FloatStorageValue =>
                "REAL"
            case value: DateStorageValue =>
                "TIMESTAMP"
            case value: DoubleStorageValue =>
                "DOUBLE"
            case value: BigDecimalStorageValue =>
                "DECIMAL"
            case value: ByteArrayStorageValue =>
                "BLOB"
            case value: ListStorageValue =>
                "SMALLINT"
            case value: ReferenceStorageValue =>
                "VARCHAR(45)"
        }
}

