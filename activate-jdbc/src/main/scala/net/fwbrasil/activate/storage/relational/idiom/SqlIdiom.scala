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

object SqlIdiom {
	def dialect(name: String) =
		name match {
			case "oracleDialect" =>
				oracleDialect
			case "mySqlDialect" =>
				mySqlDialect
			case "postgresqlDialect" =>
				postgresqlDialect
		}
}

abstract class SqlIdiom {

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
			case value: ReferenceStorageValue =>
				setValue(ps, (v: String) => ps.setString(i, v), i, value.value, Types.VARCHAR)
		}
	}

	protected def getValue[A](resultSet: ResultSet, f: => A): Option[A] = {
		val value = f
		if (resultSet.wasNull)
			None
		else
			Option(value)
	}

	def getValue(resultSet: ResultSet, i: Int, storageValue: StorageValue): StorageValue = {
		storageValue match {
			case value: IntStorageValue =>
				IntStorageValue(getValue(resultSet, resultSet.getInt(i)))
			case value: LongStorageValue =>
				LongStorageValue(getValue(resultSet, resultSet.getLong(i)))
			case value: BooleanStorageValue =>
				BooleanStorageValue(getValue(resultSet, resultSet.getBoolean(i)))
			case value: StringStorageValue =>
				StringStorageValue(getValue(resultSet, resultSet.getString(i)))
			case value: FloatStorageValue =>
				FloatStorageValue(getValue(resultSet, resultSet.getFloat(i)))
			case value: DateStorageValue =>
				DateStorageValue(getValue(resultSet, resultSet.getTimestamp(i)).map((t: Timestamp) => new Date(t.getTime)))
			case value: DoubleStorageValue =>
				DoubleStorageValue(getValue(resultSet, resultSet.getDouble(i)))
			case value: BigDecimalStorageValue =>
				BigDecimalStorageValue(getValue(resultSet, BigDecimal(resultSet.getBigDecimal(i))))
			case value: ByteArrayStorageValue =>
				ByteArrayStorageValue(getValue(resultSet, resultSet.getBytes(i)))
			case value: ReferenceStorageValue =>
				ReferenceStorageValue(getValue(resultSet, resultSet.getString(i)))
		}
	}

	def toSqlStatement(statement: StorageStatement): SqlStatement = {
		statement match {
			case insert: InsertDmlStorageStatement =>
				new SqlStatement(
					"INSERT INTO " + toTableName(insert.entityClass) +
						" (" + insert.propertyMap.keys.toList.map(escape).mkString(", ") + ") " +
						" VALUES (:" + insert.propertyMap.keys.mkString(", :") + ")",
					insert.propertyMap)

			case update: UpdateDmlStorageStatement =>
				new SqlStatement(
					"UPDATE " + toTableName(update.entityClass) +
						" SET " + (for (key <- update.propertyMap.keys) yield escape(key) + " = :" + key).mkString(", ") +
						" WHERE ID = :id",
					update.propertyMap)

			case delete: DeleteDmlStorageStatement =>
				new SqlStatement(
					"DELETE FROM " + toTableName(delete.entityClass) +
						" WHERE ID = '" + delete.entityId + "'",
					delete.propertyMap)
			case ddl: DdlStorageStatement =>
				toSqlDdl(ddl)
			case modify: ModifyStorageStatement =>
				toSqlModify(modify)
		}
	}

	def toSqlDdl(storageValue: StorageValue): String

	def toSqlDdl(column: StorageColumn): String =
		"	" + escape(column.name) + " " + column.specificTypeOption.getOrElse(toSqlDdl(column.storageValue))

	def escape(string: String): String

	def toSqlDml(statement: QueryStorageStatement): SqlStatement =
		toSqlDml(statement.query)

	def toSqlDml(query: Query[_]): SqlStatement = {
		implicit val binds = MutableMap[StorageValue, String]()
		new SqlStatement("SELECT " + toSqlDml(query.select) +
			" FROM " + toSqlDml(query.from) + " WHERE " + toSqlDml(query.where) + toSqlDmlOrderBy(query.orderByClause), (Map() ++ binds) map { _.swap })
	}

	def toSqlDml(select: Select)(implicit binds: MutableMap[StorageValue, String]): String =
		(for (value <- select.values)
			yield toSqlDmlSelect(value)).mkString(", ");

	def toSqlDmlOrderBy(orderBy: Option[OrderBy])(implicit binds: MutableMap[StorageValue, String]): String = {
		if (orderBy.isDefined)
			" ORDER BY " + toSqlDml(orderBy.get.criterias: _*)
		else ""
	}

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
				value.entitySource.name + "." + escape(value.propertyPathNames.mkString("."))
			case value: StatementEntitySourceValue[v] =>
				value.entitySource.name + ".id"
		}

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

	def toTableName(entityClass: Class[_]): String =
		escape(EntityHelper.getEntityName(entityClass))

	def toSqlDdl(statement: ModifyStorageAction): String

	def toSqlDdl(statement: DdlStorageStatement): SqlStatement =
		statement.action match {
			case action: StorageCreateTable =>
				new SqlStatement(
					toSqlDdl(action),
					ifNotExistsRestriction(findTableStatement(action.tableName), action.ifNotExists))
			case action: StorageRenameTable =>
				new SqlStatement(
					toSqlDdl(action),
					ifExistsRestriction(findTableStatement(action.oldName), action.ifExists))
			case action: StorageRemoveTable =>
				new SqlStatement(
					toSqlDdl(action),
					ifExistsRestriction(findTableStatement(action.name), action.ifExists))
			case action: StorageAddColumn =>
				new SqlStatement(
					toSqlDdl(action),
					ifNotExistsRestriction(findTableColumnStatement(action.tableName, action.column.name), action.ifNotExists))
			case action: StorageRenameColumn =>
				new SqlStatement(
					toSqlDdl(action),
					ifExistsRestriction(findTableColumnStatement(action.tableName, action.column.name), action.ifExists))
			case action: StorageRemoveColumn =>
				new SqlStatement(
					toSqlDdl(action),
					ifExistsRestriction(findTableColumnStatement(action.tableName, action.name), action.ifExists))
			case action: StorageAddIndex =>
				new SqlStatement(
					toSqlDdl(action),
					ifNotExistsRestriction(findIndexStatement(action.tableName, action.indexName), action.ifNotExists))
			case action: StorageRemoveIndex =>
				new SqlStatement(
					toSqlDdl(action),
					ifExistsRestriction(findIndexStatement(action.tableName, action.name), action.ifExists))
			case action: StorageAddReference =>
				new SqlStatement(
					toSqlDdl(action),
					ifNotExistsRestriction(findConstraintStatement(action.tableName, action.constraintName), action.ifNotExists))
			case action: StorageRemoveReference =>
				new SqlStatement(
					toSqlDdl(action),
					ifExistsRestriction(findConstraintStatement(action.tableName, action.constraintName), action.ifExists))
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
}

