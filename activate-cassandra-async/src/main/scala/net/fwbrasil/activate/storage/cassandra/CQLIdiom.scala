package net.fwbrasil.activate.storage.cassandra

import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.storage.relational.idiom.QlIdiom
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
import net.fwbrasil.activate.storage.marshalling.StorageAddReference
import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex
import net.fwbrasil.activate.statement.Operator
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.Query

object cqlIdiom extends QlIdiom {

    override def concat(strings: String*): String = strings.mkString(" + ")
    override def escape(string: String): String = string
    override def findConstraintStatement(tableName: String, constraintName: String): String = "USE ACTIVATE_TEST"
    override def findIndexStatement(tableName: String, indexName: String): String = "USE ACTIVATE_TEST"
    override def findTableColumnStatement(tableName: String, columnName: String): String = "USE ACTIVATE_TEST"
    override def findTableStatement(tableName: String): String = "USE ACTIVATE_TEST"
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
    override def toSqlDdl(storageValue: StorageValue): String =
        storageValue match {
            case value: IntStorageValue =>
                "int"
            case value: LongStorageValue =>
                "decimal"
            case value: BooleanStorageValue =>
                "boolean"
            case value: StringStorageValue =>
                "ascii"
            case value: FloatStorageValue =>
                "float"
            case value: DateStorageValue =>
                "timestamp"
            case value: DoubleStorageValue =>
                "double"
            case value: BigDecimalStorageValue =>
                "decimal"
            case value: ListStorageValue =>
                "int"
            case value: ByteArrayStorageValue =>
                "blob"
            case value: ReferenceStorageValue =>
                "ascii"
        }
    override def toSqlDmlRegexp(value: String, regex: String): String =
        value + ".*" + regex
    override def toSqlDml(value: Operator)(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case op: Or =>
                throw new UnsupportedOperationException("Cassandra does not support the OR operator.")
            case other =>
                super.toSqlDml(other)
        }

    override def versionCondition(propertyMap: Map[String, StorageValue]) = {
        propertyMap.get(versionVarName) match {
            case Some(newVersion: LongStorageValue) =>
                s" AND $versionVarName = :version - 1"
            case other =>
                ""
        }
    }

    override def toSqlDmlRemoveEntitiesReadFromCache(
        query: Query[_], entitiesReadFromCache: List[List[Entity]])(
            implicit binds: MutableMap[StorageValue, String]) = {

        def bind(id: String) =
            this.bind(StringStorageValue(Some(id)))

        val entitySources = query.from.entitySources
        val restrictions =
            for (entities <- entitiesReadFromCache) yield {
                val condition =
                    (for (i <- 0 until entitySources.size) yield {
                        val entity = entities(i)
                        val entitySource = entitySources(i)
                        entitySource.entityClass.isAssignableFrom(entity.getClass)
                        entitySources(i).name + ".id != " + bind(entity.id)
                    }).mkString(" OR ")
                s"($condition)"
            }
        if (restrictions.nonEmpty)
            " AND " + restrictions.mkString(" AND ")
        else
            ""
    }

}