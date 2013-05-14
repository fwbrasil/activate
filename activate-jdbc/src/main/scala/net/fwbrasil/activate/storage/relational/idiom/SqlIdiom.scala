package net.fwbrasil.activate.storage.relational.idiom

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.statement.StatementValue
import java.sql.ResultSet
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.StatementEntityValue
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.relational.DeleteDmlStorageStatement
import net.fwbrasil.activate.storage.relational.ModifyStorageStatement
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.storage.relational.SqlStatement
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageColumn
import net.fwbrasil.activate.statement.query.OrderBy
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.storage.relational.QueryStorageStatement
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.storage.relational.DdlStorageStatement
import net.fwbrasil.activate.statement.query.OrderByCriteria
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.relational.UpdateDmlStorageStatement
import net.fwbrasil.activate.storage.relational.StorageStatement
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.storage.relational.InsertDmlStorageStatement
import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
import net.fwbrasil.activate.storage.marshalling.StorageAddReference
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.mass.UpdateAssignment
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex
import java.sql.PreparedStatement
import net.fwbrasil.activate.statement.Operator
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.Marshaller
import java.sql.Types
import java.util.Date
import net.fwbrasil.activate.statement.query.Select
import net.fwbrasil.activate.statement.Or
import java.sql.Timestamp
import net.fwbrasil.activate.statement.Matcher
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import ch.qos.logback.classic.db.names.TableName
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.relational.DmlStorageStatement
import net.fwbrasil.activate.entity.ListEntityValue
import java.sql.Connection
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.statement.query.OrderedQuery
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.LazyListEntityValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue

object SqlIdiom {
    lazy val dialectsMap = {
        val dialects =
            Reflection
                .getAllImplementorsNames(
                    List(classOf[SqlIdiom]),
                    classOf[SqlIdiom])
                .map(Class.forName)
        for (dialect <- dialects)
            yield (dialect.getSimpleName.split('$').last ->
            Reflection.getObject[SqlIdiom](dialect))
    }.toMap
    def dialect(name: String) =
        dialectsMap.getOrElse(name, throw new IllegalArgumentException("Invalid dialect " + name))
}

trait ActivateResultSet {

    def getString(i: Int): Option[String]
    def getBytes(i: Int): Option[Array[Byte]]
    def getInt(i: Int): Option[Int]
    def getBoolean(i: Int): Option[Boolean]
    def getFloat(i: Int): Option[Float]
    def getLong(i: Int): Option[Long]
    def getTimestamp(i: Int): Option[Timestamp]
    def getDouble(i: Int): Option[Double]
    def getBigDecimal(i: Int): Option[java.math.BigDecimal]
}

case class JdbcActivateResultSet(rs: ResultSet)
        extends ActivateResultSet {

    private def getValue[T](f: => T) =
        Some(f).filter(_ => !rs.wasNull)

    def getString(i: Int) = getValue(rs.getString(i))
    def getBytes(i: Int) = getValue(rs.getBytes(i))
    def getInt(i: Int) = getValue(rs.getInt(i))
    def getBoolean(i: Int) = getValue(rs.getBoolean(i))
    def getFloat(i: Int) = getValue(rs.getFloat(i))
    def getLong(i: Int) = getValue(rs.getLong(i))
    def getTimestamp(i: Int) = getValue(rs.getTimestamp(i))
    def getDouble(i: Int) = getValue(rs.getDouble(i))
    def getBigDecimal(i: Int) = getValue(rs.getBigDecimal(i))

}

trait SqlIdiom {

    def prepareDatabase(storage: JdbcRelationalStorage) = {}

    protected def setValue[V](ps: PreparedStatement, f: (V) => Unit, i: Int, optionValue: Option[V], sqlType: Int): Unit =
        if (optionValue == None || optionValue == null)
            ps.setNull(i, sqlType)
        else
            f(optionValue.get)

    def setValue(ps: PreparedStatement, i: Int, storageValue: StorageValue): Unit = {
        storageValue match {
            case value: IntStorageValue =>
                setValue(ps, (v: Int) => ps.setInt(i, v), i, value.value, Types.INTEGER)
            case value: LongStorageValue =>
                setValue(ps, (v: Long) => ps.setLong(i, v), i, value.value, Types.DECIMAL)
            case value: BooleanStorageValue =>
                setValue(ps, (v: Boolean) => ps.setBoolean(i, v), i, value.value, Types.BIT)
            case value: StringStorageValue =>
                setValue(ps, (v: String) => ps.setString(i, v), i, value.value, Types.VARCHAR)
            case value: FloatStorageValue =>
                setValue(ps, (v: Float) => ps.setFloat(i, v), i, value.value, Types.FLOAT)
            case value: DateStorageValue =>
                setValue(ps, (v: Date) => ps.setTimestamp(i, new java.sql.Timestamp(v.getTime)), i, value.value, Types.TIMESTAMP)
            case value: DoubleStorageValue =>
                setValue(ps, (v: Double) => ps.setDouble(i, v), i, value.value, Types.DOUBLE)
            case value: BigDecimalStorageValue =>
                setValue(ps, (v: BigDecimal) => ps.setBigDecimal(i, v.bigDecimal), i, value.value, Types.BIGINT)
            case value: ByteArrayStorageValue =>
                setValue(ps, (v: Array[Byte]) => ps.setBytes(i, v), i, value.value, Types.BINARY)
            case value: ListStorageValue =>
                if (value.value.isDefined)
                    ps.setInt(i, 1)
                else
                    ps.setInt(i, 0)
            case value: ReferenceStorageValue =>
                setValue(ps, (v: String) => ps.setString(i, v), i, value.value, Types.VARCHAR)
        }
    }

    def getValue(resultSet: ResultSet, i: Int, storageValue: StorageValue): StorageValue =
        getValue(JdbcActivateResultSet(resultSet), i, storageValue)

    def getValue(resultSet: ActivateResultSet, i: Int, storageValue: StorageValue): StorageValue = {
        storageValue match {
            case value: IntStorageValue =>
                IntStorageValue(resultSet.getInt(i))
            case value: LongStorageValue =>
                LongStorageValue(resultSet.getLong(i))
            case value: BooleanStorageValue =>
                BooleanStorageValue(resultSet.getBoolean(i))
            case value: StringStorageValue =>
                StringStorageValue(resultSet.getString(i))
            case value: FloatStorageValue =>
                FloatStorageValue(resultSet.getFloat(i))
            case value: DateStorageValue =>
                DateStorageValue((resultSet.getTimestamp(i)).map((t: Timestamp) => new Date(t.getTime)))
            case value: DoubleStorageValue =>
                DoubleStorageValue(resultSet.getDouble(i))
            case value: BigDecimalStorageValue =>
                BigDecimalStorageValue(resultSet.getBigDecimal(i).map(BigDecimal(_)))
            case value: ByteArrayStorageValue =>
                ByteArrayStorageValue(resultSet.getBytes(i))
            case value: ReferenceStorageValue =>
                ReferenceStorageValue(resultSet.getString(i))
        }
    }

    private def digestLists(statement: DmlStorageStatement, mainStatementProducer: (Map[String, StorageValue] => SqlStatement)) = {
        val (normalPropertyMap, listPropertyMap) = statement.propertyMap.partition(tuple => !tuple._2.isInstanceOf[ListStorageValue])
        val isDelete = statement.isInstanceOf[DeleteDmlStorageStatement]
        val id = statement.propertyMap("id")
        val mainStatement =
            mainStatementProducer(statement.propertyMap)
        val listUpdates =
            listPropertyMap.map { tuple =>
                val (name, value) = tuple.asInstanceOf[(String, ListStorageValue)]
                val listTable = toTableName(statement.entityClass, name.capitalize)
                val delete =
                    new SqlStatement(
                        statement = "DELETE FROM " + listTable + " WHERE OWNER = :id",
                        binds = Map("id" -> id))
                val inserts =
                    if (!isDelete && value.value.isDefined) {
                        val list = value.value.get
                        (0 until list.size).map { i =>
                            new SqlStatement(
                                statement = "INSERT INTO " + listTable + " (" + escape("owner") + ", " + escape("value") + ", " + escape("pos") + ") VALUES (:owner, :value, :pos)",
                                binds = Map("owner" -> id, "value" -> list(i), "pos" -> new IntStorageValue(Some(i))))
                        }
                    } else List()
                List(delete) ++ inserts
            }.toList.flatten
        if (isDelete)
            listUpdates ++ List(mainStatement)
        else
            List(mainStatement) ++ listUpdates
    }

    def versionCondition(propertyMap: Map[String, StorageValue]) = {
        propertyMap.get(versionVarName) match {
            case Some(newVersion: LongStorageValue) =>
                s" AND ($versionVarName IS NULL OR $versionVarName = :version - 1)"
            case other =>
                ""
        }
    }

    def toSqlStatement(statement: StorageStatement): List[SqlStatement] =
        statement match {
            case insert: InsertDmlStorageStatement =>
                digestLists(insert, propertyMap =>
                    new SqlStatement(
                        statement = "INSERT INTO " + toTableName(insert.entityClass) +
                            " (" + propertyMap.keys.toList.sorted.map(escape).mkString(", ") + ") " +
                            " VALUES (:" + propertyMap.keys.toList.sorted.mkString(", :") + ")",
                        binds = propertyMap,
                        expectedNumberOfAffectedRowsOption = Some(1)))
            case update: UpdateDmlStorageStatement =>
                digestLists(update, propertyMap =>
                    new SqlStatement(
                        statement = "UPDATE " + toTableName(update.entityClass) +
                            " SET " + (for (key <- propertyMap.keys.toList.sorted if (key != "id")) yield escape(key) + " = :" + key).mkString(", ") +
                            " WHERE ID = :id" + versionCondition(propertyMap),
                        binds = propertyMap,
                        expectedNumberOfAffectedRowsOption = Some(1)))
            case delete: DeleteDmlStorageStatement =>
                digestLists(delete, propertyMap =>
                    new SqlStatement(
                        statement = "DELETE FROM " + toTableName(delete.entityClass) +
                            " WHERE ID = :id " + versionCondition(propertyMap),
                        binds = propertyMap,
                        expectedNumberOfAffectedRowsOption = Some(1)))
            case ddl: DdlStorageStatement =>
                toSqlDdlAction(ddl.action)
            case modify: ModifyStorageStatement =>
                List(toSqlModify(modify))
        }

    def toSqlDdl(storageValue: StorageValue): String

    def toSqlDdl(column: StorageColumn): String =
        "	" + escape(column.name) + " " + column.specificTypeOption.getOrElse(toSqlDdl(column.storageValue))

    def escape(string: String): String

    def toSqlDml(statement: QueryStorageStatement): SqlStatement =
        toSqlDml(statement.query, statement.entitiesReadFromCache)

    def toSqlDml(query: Query[_], entitiesReadFromCache: List[List[Entity]]): SqlStatement = {
        implicit val binds = MutableMap[StorageValue, String]()
        new SqlStatement(
            toSqlDmlQueryString(query, entitiesReadFromCache),
            (Map() ++ binds) map { _.swap })
    }

    def toSqlDmlQueryString(query: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit binds: MutableMap[StorageValue, String]) =
        "SELECT " + toSqlDml(query.select) +
            " FROM " + toSqlDml(query.from) +
            " WHERE (" + toSqlDml(query.where) + ")" +
            toSqlDmlRemoveEntitiesReadFromCache(query, entitiesReadFromCache) +
            toSqlDmlOrderBy(query)

    def toSqlDmlRemoveEntitiesReadFromCache(query: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit binds: MutableMap[StorageValue, String]) = {
        var auxIdIdx = 0
        def bind(id: String) = {
            auxIdIdx += 1
            val name = "auxId" + auxIdIdx
            binds += StringStorageValue(Some(id)) -> (name)
            name
        }

        val entitySources = query.from.entitySources
        val restrictions =
            for (entities <- entitiesReadFromCache) yield {
                val condition =
                    (for (i <- 0 until entitySources.size) yield entitySources(i).name + ".id != :" + bind(entities(i).id)).mkString(" OR ")
                s"($condition)"
            }
        if (restrictions.nonEmpty)
            " AND " + restrictions.mkString(" AND ")
        else
            ""
    }

    def toSqlDml(select: Select)(implicit binds: MutableMap[StorageValue, String]): String =
        (for (value <- select.values)
            yield toSqlDmlSelect(value)).mkString(", ");

    def toSqlDmlOrderBy(query: Query[_])(implicit binds: MutableMap[StorageValue, String]): String = {
        def orderByString = " ORDER BY " + toSqlDml(query.orderByClause.get.criterias: _*)
        query match {
            case query: LimitedOrderedQuery[_] =>
                orderByString + " " + toSqlDmlLimit(query.limit)
            case query: OrderedQuery[_] =>
                orderByString
            case other =>
                ""
        }
    }

    def toSqlDmlLimit(limit: Int): String =
        "LIMIT " + limit

    def toSqlDml(criterias: OrderByCriteria[_]*)(implicit binds: MutableMap[StorageValue, String]): String =
        (for (criteria <- criterias)
            yield toSqlDml(criteria)).mkString(", ")

    def toSqlDml(criteria: OrderByCriteria[_])(implicit binds: MutableMap[StorageValue, String]): String =
        toSqlDml(criteria.value) + " " + (
            if (criteria.direction ==
                orderByAscendingDirection)
                "asc"
            else
                "desc")

    def toSqlDml(value: StatementValue)(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: StatementBooleanValue =>
                toSqlDml(value)
            case value: StatementSelectValue[_] =>
                toSqlDmlSelect(value)
            case null =>
                null
        }

    def toSqlDmlSelect(value: StatementSelectValue[_])(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: StatementEntityValue[_] =>
                toSqlDml(value)
            case value: SimpleValue[_] =>
                toSqlDml(value)
        }

    def toSqlDml(value: SimpleValue[_])(implicit binds: MutableMap[StorageValue, String]): String =
        value.anyValue match {
            case null =>
                "is null"
            case other =>
                bind(Marshaller.marshalling(value.entityValue))
        }

    def toSqlDml(value: StatementBooleanValue)(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: SimpleStatementBooleanValue =>
                bind(Marshaller.marshalling(value.entityValue))
            case value: Criteria =>
                toSqlDml(value)
        }

    def toSqlDml[V](value: StatementEntityValue[V])(implicit binds: MutableMap[StorageValue, String]): String =
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
                        value.entitySource.name + "." + escape(propertyName)
                }
            case value: StatementEntitySourceValue[v] =>
                value.entitySource.name + ".id"
        }

    def concat(strings: String*): String

    def toSqlDml(value: From)(implicit binds: MutableMap[StorageValue, String]): String =
        (for (source <- value.entitySources)
            yield toTableName(source.entityClass) + " " + source.name).mkString(", ")

    def toSqlDml(value: Where)(implicit binds: MutableMap[StorageValue, String]): String =
        toSqlDml(value.value)

    def toSqlDml(value: Criteria)(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: BooleanOperatorCriteria =>
                toSqlDml(value.valueA) + toSqlDml(value.operator) + toSqlDml(value.valueB)
            case value: SimpleOperatorCriteria =>
                toSqlDml(value.valueA) + toSqlDml(value.operator)
            case CompositeOperatorCriteria(valueA: StatementValue, operator: Matcher, valueB: StatementValue) =>
                toSqlDmlRegexp(toSqlDml(valueA), toSqlDml(valueB))
            case value: CompositeOperatorCriteria =>
                toSqlDml(value.valueA) + toSqlDml(value.operator) + toSqlDml(value.valueB)
        }

    def toSqlDmlRegexp(value: String, regex: String): String

    def toSqlDml(value: Operator)(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: IsEqualTo =>
                " = "
            case value: IsNotEqualTo =>
                " != "
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
                " or "
            case value: IsNull =>
                " is null "
            case value: IsNotNull =>
                " is not null "
        }

    def bind(value: StorageValue)(implicit binds: MutableMap[StorageValue, String]) =
        if (binds.contains(value))
            ":" + binds(value)
        else {
            val name = binds.size.toString
            binds += (value -> name)
            ":" + name
        }

    def toTableName(entityClass: Class[_], suffix: String = ""): String =
        escape(EntityHelper.getEntityName(entityClass) + suffix)

    def toSqlDdl(action: ModifyStorageAction): String

    def toSqlDdlAction(action: ModifyStorageAction): List[SqlStatement] =
        action match {
            case action: StorageCreateListTable =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findTableStatement(action.listTableName), action.ifNotExists))) ++
                    toSqlDdlAction(action.addOwnerIndexAction)
            case action: StorageRemoveListTable =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableStatement(action.listTableName), action.ifExists)))
            case action: StorageCreateTable =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findTableStatement(action.tableName), action.ifNotExists)))
            case action: StorageRenameTable =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableStatement(action.oldName), action.ifExists)))
            case action: StorageRemoveTable =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableStatement(action.name), action.ifExists)))
            case action: StorageAddColumn =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findTableColumnStatement(action.tableName, action.column.name), action.ifNotExists)))
            case action: StorageRenameColumn =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableColumnStatement(action.tableName, action.oldName), action.ifExists)))
            case action: StorageRemoveColumn =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableColumnStatement(action.tableName, action.name), action.ifExists)))
            case action: StorageAddIndex =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findIndexStatement(action.tableName, action.indexName), action.ifNotExists)))
            case action: StorageRemoveIndex =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findIndexStatement(action.tableName, action.name), action.ifExists)))
            case action: StorageAddReference =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findConstraintStatement(action.tableName, action.constraintName), action.ifNotExists)))
            case action: StorageRemoveReference =>
                List(new SqlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findConstraintStatement(action.tableName, action.constraintName), action.ifExists)))
        }

    def toSqlModify(statement: ModifyStorageStatement) = {
        implicit val binds = MutableMap[StorageValue, String]()
        statement.statement match {
            case update: MassUpdateStatement =>
                new SqlStatement(
                    removeAlias("UPDATE " + toSqlDml(update.from) + " SET " + toSqlDml(update.assignments.toList) + " WHERE " + toSqlDml(update.where), update.from),
                    (Map() ++ binds) map { _.swap })
            case delete: MassDeleteStatement =>
                new SqlStatement(
                    removeAlias("DELETE FROM " + toSqlDml(delete.from) + " WHERE " + toSqlDml(delete.where), delete.from),
                    (Map() ++ binds) map { _.swap })
        }
    }

    private def removeAlias(sql: String, from: From) = {
        var result = sql
        for (entitySource <- from.entitySources) {
            result = result.replaceAll(entitySource.name + ".", "")
            result = result.replaceAll(entitySource.name, "")
        }
        result
    }

    def toSqlDml(assignments: List[UpdateAssignment])(implicit binds: MutableMap[StorageValue, String]): String =
        assignments.map(toSqlDml).mkString(", ")

    def toSqlDml(assignment: UpdateAssignment)(implicit binds: MutableMap[StorageValue, String]): String = {
        val value = assignment.value match {
            case value: SimpleValue[_] =>
                if (value.anyValue == null)
                    "null"
                else
                    toSqlDml(value)
            case other =>
                toSqlDml(other)
        }
        toSqlDml(assignment.assignee) + " = " + value
    }

    def findTableStatement(tableName: String): String

    def findTableColumnStatement(tableName: String, columnName: String): String

    def findIndexStatement(tableName: String, indexName: String): String

    def findConstraintStatement(tableName: String, constraintName: String): String

    def ifExistsRestriction(statement: String, boolean: Boolean) =
        if (boolean)
            Option(statement, 1)
        else
            None

    def ifNotExistsRestriction(statement: String, boolean: Boolean) =
        if (boolean)
            Option(statement, 0)
        else
            None

    private def listColumnSelect(value: StatementEntitySourcePropertyValue[_], propertyName: String) = {
        val listTableName = toTableName(value.entitySource.entityClass, propertyName.capitalize)
        val res = concat(value.entitySource.name + "." + propertyName, "'|'", "'SELECT VALUE FROM " + listTableName + " WHERE OWNER = '''", value.entitySource.name + ".id", "''' ORDER BY POS'")
        res
    }
}

