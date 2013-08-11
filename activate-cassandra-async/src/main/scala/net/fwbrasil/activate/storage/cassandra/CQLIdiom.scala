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

object cqlIdiom extends QlIdiom {

    override def concat(strings: String*): String = strings.mkString(" + ")

    override def escape(string: String): String = string

    override def toSqlDdlAction(action: ModifyStorageAction): List[NormalQlStatement] =
        List(new NormalQlStatement(toSqlDdl(action)))

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
            case value: IsNull =>
                " = null "
            case value: IsNotNull =>
                " != null "
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

        val entitySources = query.from.entitySources
        val restrictions =
            for (entities <- entitiesReadFromCache) yield {
                val condition =
                    (for (i <- 0 until entitySources.size) yield {
                        val entity = entities(i)
                        val entitySource = entitySources(i)
                        entitySources(i).name + ".id != " + bindId(entity.id)
                    }).mkString(" OR ")
                s"($condition)"
            }
        if (restrictions.nonEmpty)
            " AND " + restrictions.mkString(" AND ")
        else
            ""
    }

    override def toSqlDmlQueryString(query: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit binds: MutableMap[StorageValue, String]) =
        "SELECT " + toSqlDml(query.select) +
            " FROM " + toSqlDml(query.from) +
            " WHERE " + toSqlDml(query.where) +
            toSqlDmlRemoveEntitiesReadFromCache(query, entitiesReadFromCache) +
            toSqlDmlOrderBy(query)

    override def toSqlDml[V](value: StatementEntityValue[V])(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: StatementEntityInstanceValue[_] =>
                bind(StringStorageValue(Option(value.entityId)))
            case value: StatementEntitySourcePropertyValue[v] =>
                val propertyName = value.propertyPathNames.mkString(".")
                value.entityValue match {
                    case entityValue: ListEntityValue[_] =>
                        listColumnSelect(value, propertyName)
                    case entityValue: LazyListEntityValue[_] =>
                        listColumnSelect(value, propertyName)
                    case other =>
                        escape(propertyName)
                }
            case value: StatementEntitySourceValue[v] =>
                "id"
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

}