package net.fwbrasil.activate.storage.relational.idiom

import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.statement.query.Select
import scala.collection.mutable.{ Map => MutableMap }
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
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
import java.sql.SQLException
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.statement.query.OrderByCriteria
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.storage.marshalling.StorageModifyColumnType
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import com.zaxxer.hikari.HikariConfig

object derbyRegex {
    def regexp(src: String, pattern: String) = {
        if (src != null && src.matches(pattern))
            1
        else
            0
    }
}

object derbyDialect extends derbyDialect(pEscape = string => "\"" + string + "\"", pNormalize = string => string.toUpperCase) {
    def apply(escape: String => String = string => "\"" + string + "\"", normalize: String => String = string => string.toUpperCase) =
        new postgresqlDialect(escape, normalize)
}

class derbyDialect(pEscape: String => String, pNormalize: String => String) extends SqlIdiom {

    override def escape(string: String) =
        pEscape(pNormalize(string))

    override def prepareDatabase(storage: JdbcRelationalStorage) =
        try {
            storage.executeWithTransaction {
                _.prepareStatement(
                    "create function REGEXP(src varchar(3000), pattern varchar(128)) " +
                        "returns int " +
                        "language java " +
                        "parameter style java " +
                        "no sql " +
                        "external name 'net.fwbrasil.activate.storage.relational.idiom.derbyRegex.regexp'").executeUpdate
            }
        } catch {
            case e: SQLException =>
                if (!e.getMessage.contains("REGEXP"))
                    throw e
        }

    override def hikariConfigFor(storage: PooledJdbcRelationalStorage, jdbcDataSourceName: String) = {
        import storage._
        val config = new HikariConfig
        val (url, connectionAttributes) =
            storage.url.split(";").toList match {
                case url :: connectionAttributes :: Nil =>
                    (url, connectionAttributes)
                case url :: Nil =>
                    (url, "")
                case other =>
                    throw new IllegalStateException("Invalid derby url")
            }
        config.addDataSourceProperty("databaseName", url.replace("jdbc:derby:", ""))
        config.addDataSourceProperty("connectionAttributes", connectionAttributes)
        config.setDataSourceClassName(jdbcDataSourceName)
        user map { u => config.addDataSourceProperty("user", u) }
        password map { p => config.addDataSourceProperty("password", p) }
        config.setAutoCommit(true)
        config.setMaximumPoolSize(poolSize)
        config
    }

    def toSqlDmlRegexp(value: String, regex: String) =
        "REGEXP(" + value + ", " + regex + ")=1"

    override def findTableStatement(tableName: String) =
        "SELECT COUNT(1) " +
            "  FROM SYS.SYSTABLES ST, SYS.SYSSCHEMAS SS " +
            " WHERE ST.SCHEMAID = SS.SCHEMAID " +
            "   AND SS.SCHEMANAME = CURRENT SCHEMA" +
            "   AND ST.TABLENAME = '" + pNormalize(tableName) + "'"

    override def findTableColumnStatement(tableName: String, columnName: String) =
        "SELECT COUNT(1) " +
            "  FROM SYS.SYSCOLUMNS SC, SYS.SYSTABLES ST, SYS.SYSSCHEMAS SS " +
            " WHERE ST.SCHEMAID = SS.SCHEMAID " +
            "   AND SS.SCHEMANAME = CURRENT SCHEMA" +
            "   AND ST.TABLENAME = '" + pNormalize(tableName) + "'" +
            "   AND SC.COLUMNNAME = '" + pNormalize(columnName) + "'" +
            "   AND SC.REFERENCEID = ST.TABLEID"

    override def findIndexStatement(tableName: String, indexName: String) =
        "SELECT COUNT(1) " +
            "  FROM SYS.SYSCONGLOMERATES SG, SYS.SYSTABLES ST, SYS.SYSSCHEMAS SS " +
            " WHERE ST.SCHEMAID = SS.SCHEMAID " +
            "   AND SS.SCHEMANAME = CURRENT SCHEMA" +
            "   AND ST.TABLENAME = '" + pNormalize(tableName) + "'" +
            "   AND SG.CONGLOMERATENAME = '" + pNormalize(indexName) + "'" +
            "   AND SG.TABLEID = ST.TABLEID"

    override def findConstraintStatement(tableName: String, constraintName: String): String =
        "SELECT COUNT(1) " +
            "  FROM SYS.SYSCONSTRAINTS SC, SYS.SYSTABLES ST, SYS.SYSSCHEMAS SS " +
            " WHERE ST.SCHEMAID = SS.SCHEMAID " +
            "   AND SS.SCHEMANAME = CURRENT SCHEMA" +
            "   AND ST.TABLENAME = '" + pNormalize(tableName) + "'" +
            "   AND SC.CONSTRAINTNAME = '" + pNormalize(constraintName) + "'" +
            "   AND SC.TABLEID = ST.TABLEID"

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
                "RENAME TABLE " + escape(oldName) + " TO " + escape(newName)
            case StorageRemoveTable(name, ifExists, isCascade) =>
                "DROP TABLE " + escape(name)
            case StorageAddColumn(tableName, column, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
            case StorageRenameColumn(tableName, oldName, column, ifExists) =>
                "RENAME COLUMN " + escape(tableName) + "." + escape(oldName) + " TO " + escape(column.name)
            case StorageModifyColumnType(tableName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(column.name) + " SET DATA TYPE " + columnType(column)
            case StorageRemoveColumn(tableName, name, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
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

    override def concat(strings: String*) =
        strings.mkString(" || ")

    override def toSqlDmlLimit(query: LimitedOrderedQuery[_]): String =
        query.offsetOption.map("OFFSET " + _ + " ROWS ").getOrElse("") + s"FETCH FIRST ${query.limit} ROWS ONLY"

    override def toSqlDml(criteria: OrderByCriteria[_])(implicit binds: MutableMap[StorageValue, String]): String =
        super.toSqlDml(criteria) + " NULLS FIRST"

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
                "FLOAT"
            case value: DateStorageValue =>
                "TIMESTAMP"
            case value: DoubleStorageValue =>
                "DOUBLE PRECISION"
            case value: BigDecimalStorageValue =>
                "DECIMAL"
            case value: ByteArrayStorageValue =>
                "LONG VARCHAR FOR BIT DATA"
            case value: ListStorageValue =>
                "VARCHAR(1)"
            case value: ReferenceStorageValue =>
                "VARCHAR(45)"
        }

    override def toSqlDml(select: Select)(implicit binds: MutableMap[StorageValue, String]): String = {
        (for (value <- select.values) yield value match {
            case value: SimpleValue[_] => "cast (" + toSqlDmlSelect(value) + " as " + typeOf(value) + ")"
            case other => toSqlDmlSelect(value)
        }).mkString(", ")
    }

    private def typeOf(value: SimpleValue[_]) =
        toSqlDdl(Marshaller.marshalling(value.entityValue))
}

