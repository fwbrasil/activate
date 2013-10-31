package net.fwbrasil.activate.storage.relational.idiom

import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.LazyListEntityValue
import net.fwbrasil.activate.entity.ListEntityValue
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.FunctionApply
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.statement.Operator
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementEntityValue
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.ToLowerCase
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.mass.UpdateAssignment
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.statement.query.OrderByCriteria
import net.fwbrasil.activate.statement.query.OrderedQuery
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.query.Select
import net.fwbrasil.activate.statement.query._
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
import net.fwbrasil.activate.storage.marshalling.StorageAddReference
import net.fwbrasil.activate.storage.marshalling.StorageColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.relational.DdlStorageStatement
import net.fwbrasil.activate.storage.relational.DeleteStorageStatement
import net.fwbrasil.activate.storage.relational.DmlStorageStatement
import net.fwbrasil.activate.storage.relational.InsertStorageStatement
import net.fwbrasil.activate.storage.relational.ModifyStorageStatement
import net.fwbrasil.activate.storage.relational.NormalQlStatement
import net.fwbrasil.activate.storage.relational.QueryStorageStatement
import net.fwbrasil.activate.storage.relational.StorageStatement
import net.fwbrasil.activate.storage.relational.UpdateStorageStatement
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageModifyColumnType
import net.fwbrasil.activate.statement.EntitySource
import net.fwbrasil.activate.entity.EntityPropertyMetadata
import net.fwbrasil.activate.statement.ListValue
import net.fwbrasil.activate.statement.In
import net.fwbrasil.activate.statement.NotIn

trait QlIdiom {

    private def digestLists(statement: DmlStorageStatement, mainStatementProducer: (Map[String, StorageValue] => NormalQlStatement)) = {
        val (normalPropertyMap, listPropertyMap) = statement.propertyMap.partition(tuple => !tuple._2.isInstanceOf[ListStorageValue])
        val isDelete = statement.isInstanceOf[DeleteStorageStatement]
        val id = statement.propertyMap("id")
        val mainStatement =
            mainStatementProducer(statement.propertyMap)
        val listUpdates =
            listPropertyMap.map { tuple =>
                val (name, value) = tuple.asInstanceOf[(String, ListStorageValue)]
                val (listColumn, listTable) = EntityPropertyMetadata.nestedListNamesFor(statement.entityClass, name)
                val delete =
                    new NormalQlStatement(
                        statement = "DELETE FROM " + escape(listTable) + " WHERE OWNER = :id",
                        binds = Map("id" -> id))
                val inserts =
                    if (!isDelete && value.value.isDefined) {
                        val list = value.value.get
                        (0 until list.size).map { i =>
                            new NormalQlStatement(
                                statement = "INSERT INTO " + escape(listTable) + " (" + escape("owner") + ", " + escape("value") + ", " + escape("POS") + ") VALUES (:owner, :value, :pos)",
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

    def toSqlStatement(statement: StorageStatement): List[NormalQlStatement] =
        statement match {
            case insert: InsertStorageStatement =>
                digestLists(insert, propertyMap =>
                    new NormalQlStatement(
                        statement = "INSERT INTO " + toTableName(insert.entityClass) +
                            " (" + propertyMap.keys.toList.sorted.map(escape).mkString(", ") + ") " +
                            " VALUES (:" + propertyMap.keys.toList.sorted.mkString(", :") + ")",
                        binds = propertyMap,
                        expectedNumberOfAffectedRowsOption = Some(1)))
            case update: UpdateStorageStatement =>
                digestLists(update, propertyMap =>
                    new NormalQlStatement(
                        statement = "UPDATE " + toTableName(update.entityClass) +
                            " SET " + (for (key <- propertyMap.keys.toList.sorted if (key != "id")) yield escape(key) + " = :" + key).mkString(", ") +
                            " WHERE ID = :id" + versionCondition(propertyMap),
                        binds = propertyMap,
                        expectedNumberOfAffectedRowsOption = Some(1)))
            case delete: DeleteStorageStatement =>
                digestLists(delete, propertyMap =>
                    new NormalQlStatement(
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

    def toSqlDdlAction(action: ModifyStorageAction): List[NormalQlStatement]

    def toSqlDdl(column: StorageColumn): String =
        "	" + escape(column.name) + " " + columnType(column)

    def columnType(column: StorageColumn) =
        column.specificTypeOption.getOrElse(toSqlDdl(column.storageValue))

    def escape(string: String): String

    def toSqlDml(statement: QueryStorageStatement): NormalQlStatement =
        toSqlDml(statement.query, statement.entitiesReadFromCache)

    def toSqlDml(query: Query[_], entitiesReadFromCache: List[List[Entity]]): NormalQlStatement = {
        implicit val binds = MutableMap[StorageValue, String]()
        new NormalQlStatement(
            toSqlDmlQueryString(query, entitiesReadFromCache),
            (Map() ++ binds) map { _.swap })
    }

    def toSqlDmlQueryString(query: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit binds: MutableMap[StorageValue, String]) =
        "SELECT " + toSqlDml(query.select) +
            " FROM " + toSqlDml(query.from) +
            toSqlDml(query.where) +
            toSqlDmlRemoveEntitiesReadFromCache(query, entitiesReadFromCache) +
            toSqlDmlOrderBy(query)

    def toSqlDmlRemoveEntitiesReadFromCache(
        query: Query[_], entitiesReadFromCache: List[List[Entity]])(
            implicit binds: MutableMap[StorageValue, String]) = {

        val entitySources = query.from.entitySources

        val restrictions =
            for (entities <- entitiesReadFromCache) yield {
                (for (i <- 0 until entitySources.size) yield {
                    notEqualId(entities(i), entitySources(i))
                }).flatten.mkString(" AND ")
            }
        val base =
            if (query.where.valueOption.isDefined)
                " AND "
            else
                " WHERE "
        if (restrictions.nonEmpty)
            base + restrictions.mkString(" AND ")
        else
            ""
    }

    def notEqualId(entity: Entity, entitySource: EntitySource)(
        implicit binds: MutableMap[StorageValue, String]) =
        List(entitySource.name + ".id != " + bindId(entity.id))

    def bindId(id: String)(implicit binds: MutableMap[StorageValue, String]) =
        bind(StringStorageValue(Some(id)))

    def toSqlDml(select: Select)(implicit binds: MutableMap[StorageValue, String]): String =
        (for (value <- select.values)
            yield toSqlDmlSelect(value)).mkString(", ");

    def toSqlDmlOrderBy(query: Query[_])(implicit binds: MutableMap[StorageValue, String]): String = {
        def orderByString = " ORDER BY " + toSqlDml(query.orderByClause.get.criterias: _*)
        query match {
            case query: LimitedOrderedQuery[_] =>
                orderByString + " " + toSqlDmlLimit(query)
            case query: OrderedQuery[_] =>
                orderByString
            case other =>
                ""
        }
    }

    def toSqlDmlLimit(query: LimitedOrderedQuery[_]): String =
        "LIMIT " + query.limit + query.offsetOption.map(" OFFSET " + _).getOrElse("")

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
            case value: ListValue[_] =>
                "(" + value.statementSelectValueList.map(toSqlDml).mkString(",") + ")"
            case value: StatementBooleanValue =>
                toSqlDml(value)
            case value: StatementSelectValue =>
                toSqlDmlSelect(value)
            case null =>
                null
        }

    def toSqlDmlSelect(value: StatementSelectValue)(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: FunctionApply[_] =>
                toSqlDmlFunctionApply(value)
            case value: StatementEntityValue[_] =>
                toSqlDml(value)
            case value: SimpleValue[_] =>
                toSqlDml(value)
        }

    def toSqlDmlFunctionApply(value: FunctionApply[_])(implicit binds: MutableMap[StorageValue, String]): String =
        value match {
            case value: ToUpperCase =>
                stringUpperFunction(toSqlDml(value.value))
            case value: ToLowerCase =>
                stringLowerFunction(toSqlDml(value.value))
        }

    def stringUpperFunction(value: String): String = s"UPPER($value)"
    def stringLowerFunction(value: String): String = s"LOWER($value)"

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
            case value: StatementEntitySourcePropertyValue =>
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
        value.valueOption.map {
            value => " WHERE (" + toSqlDml(value) + ")"
        }.getOrElse("")

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
            case value: In =>
                " in "
                case value: NotIn =>
                " not in "
        }

    def bind(value: StorageValue)(implicit binds: MutableMap[StorageValue, String]) =
        if (binds.contains(value))
            ":" + binds(value)
        else {
            val name = binds.size.toString
            binds += (value -> name)
            ":" + name
        }

    def toTableName(entityClass: Class[_]): String =
        escape(EntityHelper.getEntityName(entityClass))

    def toSqlModify(statement: ModifyStorageStatement) = {
        implicit val binds = MutableMap[StorageValue, String]()
        statement.statement match {
            case update: MassUpdateStatement =>
                new NormalQlStatement(
                    removeAlias("UPDATE " + toSqlDml(update.from) + " SET " + toSqlDml(update.assignments.toList) + toSqlDml(update.where), update.from),
                    (Map() ++ binds) map { _.swap })
            case delete: MassDeleteStatement =>
                new NormalQlStatement(
                    removeAlias("DELETE FROM " + toSqlDml(delete.from) + toSqlDml(delete.where), delete.from),
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

    def toSqlDdl(action: ModifyStorageAction): String = {
        action match {
            case StorageRemoveListTable(listTableName, ifNotExists) =>
                "DROP TABLE " + escape(listTableName)
            case StorageCreateListTable(ownerTableName, listTableName, valueColumn, orderColumn, ifNotExists) =>
                "CREATE TABLE " + escape(listTableName) + "(\n" +
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
            case StorageModifyColumnType(tableName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(column.name) + " SET DATA TYPE " + columnType(column)
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

    def listColumnSelect(value: StatementEntitySourcePropertyValue, propertyName: String) = {
        val (listColumn, listTable) = EntityPropertyMetadata.nestedListNamesFor(value.entitySource.entityClass, propertyName)
        val res = concat(value.entitySource.name + "." + escape(listColumn), "'|'", "'SELECT VALUE FROM " + escape(listTable) + " WHERE OWNER = '''", value.entitySource.name + ".id", "''' ORDER BY " + escape("POS") + "'")
        res
    }

}