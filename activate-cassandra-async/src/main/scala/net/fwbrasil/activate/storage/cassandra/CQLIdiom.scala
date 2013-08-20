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
import net.fwbrasil.activate.storage.relational.NormalQlStatement
import net.fwbrasil.activate.storage.relational.NormalQlStatement
import net.fwbrasil.activate.statement.StatementEntityValue
import net.fwbrasil.activate.entity.ListEntityValue
import net.fwbrasil.activate.entity.LazyListEntityValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.storage.relational.InsertStorageStatement
import net.fwbrasil.activate.storage.relational.ModifyStorageStatement
import net.fwbrasil.activate.storage.relational.DdlStorageStatement
import net.fwbrasil.activate.storage.relational.StorageStatement
import net.fwbrasil.activate.storage.relational.DeleteStorageStatement
import net.fwbrasil.activate.storage.relational.UpdateStorageStatement
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.statement.EntitySource

object cqlIdiom extends QlIdiom {

    override def concat(strings: String*): String = strings.mkString(" + ")

    override def escape(string: String): String = "\"" + string + "\""

    override def toSqlDdlAction(action: ModifyStorageAction): List[NormalQlStatement] =
        action match {
            case action: StorageCreateListTable =>
                List()
            case action: StorageRemoveListTable =>
                List()
            case action: StorageRemoveColumn =>
                List()
            case other =>
                List(new NormalQlStatement(toSqlDdl(other)))
        }

    override def toSqlDdl(storageValue: StorageValue): String =
        storageValue match {
            case value: IntStorageValue =>
                "int"
            case value: LongStorageValue =>
                "bigint"
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
                "list<" + toSqlDdl(value.emptyStorageValue) + ">"
            case value: ByteArrayStorageValue =>
                "blob"
            case value: ReferenceStorageValue =>
                "ascii"
        }

    override def toSqlDmlRegexp(value: String, regex: String): String =
        value + ".*" + regex

    override def toSqlDml(value: Operator)(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: IsEqualTo =>
                " = "
            case value: IsNotEqualTo =>
                operatorNotSupported(value)
            case value: IsGreaterThan =>
                " > "
            case value: IsLessThan =>
                " < "
            case value: IsGreaterOrEqualTo =>
                " >= "
            case value: IsLessOrEqualTo =>
                " <= "
            case value: And =>
                " and "
            case value: Or =>
                operatorNotSupported(value)
            case value: IsNull =>
                " = null "
            case value: IsNotNull =>
                operatorNotSupported(value)
        }

    private def operatorNotSupported(value: Operator) =
        throw new UnsupportedOperationException(s"Cassandra does not support the $value operator.")

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
            implicit binds: MutableMap[StorageValue, String]) =
        ""

    override def notEqualId(entity: Entity, entitySource: EntitySource)(
        implicit binds: MutableMap[StorageValue, String]) =
        List()
    //        List("token(id) < token(" + bindId(entity.id) + ")",
    //            "token(id) > token(" + bindId(entity.id) + ")")

    override def toSqlDml(value: Where)(implicit binds: MutableMap[StorageValue, String]): String =
        value.valueOption.map {
            value => " WHERE " + toSqlDml(value)
        }.getOrElse("")

    override def toSqlDml[V](value: StatementEntityValue[V])(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: StatementEntityInstanceValue[_] =>
                bind(StringStorageValue(Option(value.entityId)))
            case value: StatementEntitySourcePropertyValue[v] =>
                val propertyName = value.propertyPathNames.mkString(".")
                value.entityValue match {
                    //                    case entityValue: ListEntityValue[_] =>
                    //                        listColumnSelect(value, propertyName)
                    //                    case entityValue: LazyListEntityValue[_] =>
                    //                        listColumnSelect(value, propertyName)
                    case other =>
                        escape(propertyName)
                }
            case value: StatementEntitySourceValue[v] =>
                "id"
        }

    override def toSqlStatement(statement: StorageStatement): List[NormalQlStatement] =
        statement match {
            case insert: InsertStorageStatement =>
                List(new NormalQlStatement(
                    statement = "INSERT INTO " + toTableName(insert.entityClass) +
                        " (" + insert.propertyMap.keys.toList.sorted.map(escape).mkString(", ") + ") " +
                        " VALUES (:" + insert.propertyMap.keys.toList.sorted.mkString(", :") + ")",
                    binds = insert.propertyMap,
                    expectedNumberOfAffectedRowsOption = Some(1)))
            case update: UpdateStorageStatement =>
                List(new NormalQlStatement(
                    statement = "UPDATE " + toTableName(update.entityClass) +
                        " SET " + (for (key <- update.propertyMap.keys.toList.sorted if (key != "id")) yield escape(key) + " = :" + key).mkString(", ") +
                        " WHERE ID = :id" + versionCondition(update.propertyMap),
                    binds = update.propertyMap,
                    expectedNumberOfAffectedRowsOption = Some(1)))
            case delete: DeleteStorageStatement =>
                List(new NormalQlStatement(
                    statement = "DELETE FROM " + toTableName(delete.entityClass) +
                        " WHERE ID = :id " + versionCondition(delete.propertyMap),
                    binds = delete.propertyMap,
                    expectedNumberOfAffectedRowsOption = Some(1)))
            case ddl: DdlStorageStatement =>
                toSqlDdlAction(ddl.action)
            case modify: ModifyStorageStatement =>
                List(toSqlModify(modify))
        }

    override def listColumnSelect(value: StatementEntitySourcePropertyValue[_], propertyName: String) = {
        val listTableName = toTableName(value.entitySource.entityClass, propertyName.capitalize)
        val res = concat(escape(propertyName), "'|'", "'SELECT VALUE FROM " + listTableName + " WHERE OWNER = '''", value.entitySource.name + ".id", "''' ORDER BY " + escape("POS") + "'")
        res
    }

    override def toSqlDml(value: From)(implicit binds: MutableMap[StorageValue, String]): String =
        (for (source <- value.entitySources)
            yield toTableName(source.entityClass)).mkString(", ")

    override def toSqlDml(value: SimpleValue[_])(implicit binds: MutableMap[StorageValue, String]): String =
        value.anyValue match {
            case null =>
                "= null"
            case other =>
                bind(Marshaller.marshalling(value.entityValue))
        }

    override def toSqlDdl(action: ModifyStorageAction): String = {
        action match {
            case StorageCreateListTable(ownerTableName, listName, valueColumn, orderColumn, ifNotExists) =>
                "CREATE TABLE " + escape(ownerTableName + listName.capitalize) + "(\n" +
                    "	" + escape("owner") + " " + toSqlDdl(ReferenceStorageValue(None)) + ",\n" +
                    toSqlDdl(valueColumn) + ", " + toSqlDdl(orderColumn) +
                    ")"
            case StorageAddIndex(tableName, columnName, indexName, ifNotExists, unique) =>
                "CREATE INDEX " + indexName + " ON " + escape(tableName) + " (" + escape(columnName) + ")"
            case StorageRemoveTable(name, ifExists, isCascade) =>
                "DROP TABLE " + escape(name)
            case StorageRemoveColumn(tableName, name, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP " + escape(name)
            case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                throw new UnsupportedOperationException("Cassandra hasn't references.")
            case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                throw new UnsupportedOperationException("Cassandra hasn't references.")
            case other =>
                super.toSqlDdl(other)
        }
    }

}