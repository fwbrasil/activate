package net.fwbrasil.activate.storage.relational

import java.util.regex.Pattern
import net.fwbrasil.activate.query._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.serialization.Serializator
import scala.collection.mutable.{ Map => MutableMap, ListBuffer }
import java.sql.PreparedStatement
import net.fwbrasil.activate.storage.marshalling._
import java.sql.Types
import java.sql.Timestamp
import java.sql.ResultSet
import java.util.Date
import java.util.Calendar
import net.fwbrasil.activate.migration.MigrationAction
import net.fwbrasil.activate.migration.CreateTable
import net.fwbrasil.activate.migration.RenameTable
import net.fwbrasil.activate.migration.RemoveTable
import net.fwbrasil.activate.migration.AddColumn
import net.fwbrasil.activate.migration.RenameColumn
import net.fwbrasil.activate.migration.RemoveColumn
import net.fwbrasil.activate.migration.AddIndex
import net.fwbrasil.activate.migration.RemoveIndex
import net.fwbrasil.activate.migration.Column

case class SqlStatement(statement: String, binds: Map[String, StorageValue]) {

	// TODO Fazer em 3 linhas!
	def toIndexedBind = {
		val pattern = Pattern.compile("(:[a-zA-Z]*[0-9]*)")
		var matcher = pattern.matcher(statement)
		val values = new ListBuffer[StorageValue]()
		var result = statement
		matcher.matches
		while (matcher.find) {
			val group = matcher.group
			result = matcher.replaceFirst("?")
			matcher = pattern.matcher(result)
			values += binds(group.substring(1))
		}
		(result, values)
	}

}

abstract class SqlIdiom {

	protected def setValue[V](ps: PreparedStatement, f: (V) => Unit, i: Int, optionValue: Option[V], sqlType: Int): Unit =
		if (optionValue == None || optionValue == null)
			ps.setNull(i, sqlType)
		else
			f(optionValue.get)

	def setValue(ps: PreparedStatement, i: Int, storageValue: StorageValue): Unit = {
		storageValue match {
			case value: IntStorageValue =>
				setValue(ps, (v: Int) => ps.setInt(i, v), i, value.value, Types.INTEGER)
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
				setValue(ps, (v: Array[Byte]) => ps.setBytes(i, v), i, value.value, Types.BLOB)
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
				SqlStatement(
					"INSERT INTO " + toTableName(insert.entityClass) +
						" (" + insert.propertyMap.keys.mkString(", ") + ") " +
						" VALUES (:" + insert.propertyMap.keys.mkString(", :") + ")",
					insert.propertyMap)

			case update: UpdateDmlStorageStatement =>
				SqlStatement(
					"UPDATE " + toTableName(update.entityClass) +
						" SET " + (for (key <- update.propertyMap.keys) yield key + " = :" + key).mkString(", ") +
						" WHERE ID = :id",
					update.propertyMap)

			case delete: DeleteDmlStorageStatement =>
				SqlStatement(
					"DELETE FROM " + toTableName(delete.entityClass) +
						" WHERE ID = '" + delete.entityId + "'",
					delete.propertyMap)
			case ddl: DdlStorageStatement =>
				toSqlDdl(ddl)
		}
	}

	def toSqlDdl(storageValue: StorageValue): String

	def toSqlDdl(column: StorageColumn): String =
		"	" + column.name + " " + toSqlDdl(column.storageValue)

	def toSqlDml(statement: QueryStorageStatement): SqlStatement =
		toSqlDml(statement.query)

	def toSqlDml(query: Query[_]): SqlStatement = {
		implicit val binds = MutableMap[StorageValue, String]()

		SqlStatement("SELECT DISTINCT " + toSqlDml(query.select) +
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

	def toSqlDml(value: QueryValue)(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: QueryBooleanValue =>
				toSqlDml(value)
			case value: QuerySelectValue[_] =>
				toSqlDmlSelect(value)
		}

	def toSqlDmlSelect(value: QuerySelectValue[_])(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: QueryEntityValue[_] =>
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

	def toSqlDml(value: QueryBooleanValue)(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: SimpleQueryBooleanValue =>
				bind(Marshaller.marshalling(value.entityValue))
			case value: Criteria =>
				toSqlDml(value)
		}

	def toSqlDml[V](value: QueryEntityValue[V])(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: QueryEntityInstanceValue[Entity] =>
				bind(StringStorageValue(Option(value.entityId)))
			case value: QueryEntitySourcePropertyValue[v] =>
				value.entitySource.name + "." + value.propertyPathNames.mkString(".")
			case value: QueryEntitySourceValue[v] =>
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
			case CompositeOperatorCriteria(valueA: QueryValue, operator: Matcher, valueB: QueryValue) =>
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
		EntityHelper.getEntityName(entityClass)

	def toSqlDdl(statement: DdlStorageStatement): SqlStatement = {
		statement.action match {
			case StorageCreateTable(tableName, columns) =>
				SqlStatement(
					"CREATE TABLE " + tableName + "(\n" +
						"	ID " + toSqlDdl(StringStorageValue(None)) + " PRIMARY KEY,\n" +
						columns.map(toSqlDdl).mkString(", \n") +
						")",
					Map())
			case StorageRenameTable(oldName, newName) =>
				SqlStatement(
					"RENAME TABLE " + oldName + " TO " + newName,
					Map())
			case StorageRemoveTable(name) =>
				SqlStatement(
					"DROP TABLE " + name,
					Map())
			case StorageAddColumn(tableName, column) =>
				SqlStatement(
					"ALTER TABLE " + tableName + " ADD " + toSqlDdl(column),
					Map())
			case StorageRenameColumn(tableName, oldName, column) =>
				SqlStatement(
					"ALTER TABLE " + tableName + " CHANGE " + oldName + " " + toSqlDdl(column),
					Map())
			case StorageRemoveColumn(tableName, name) =>
				SqlStatement(
					"ALTER TABLE " + tableName + " DROP COLUMN " + name,
					Map())
			case StorageAddIndex(tableName, columnName, indexName) =>
				SqlStatement(
					"CREATE INDEX " + indexName + " ON " + tableName + " (" + columnName + ")",
					Map())
			case StorageRemoveIndex(tableName, name) =>
				SqlStatement(
					"DROP INDEX " + name + " ON " + tableName,
					Map())
		}
	}

}

object mySqlDialect extends SqlIdiom {

	override def getValue(resultSet: ResultSet, i: Int, storageValue: StorageValue) = {
		storageValue match {
			case value: DateStorageValue =>
				DateStorageValue(getValue(resultSet, resultSet.getLong(i)).map((t: Long) => new Date(t)))
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

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
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
			case value: ByteArrayStorageValue =>
				"BLOB"
			case value: ReferenceStorageValue =>
				"VARCHAR(200)"
		}
}

object postgresqlDialect extends SqlIdiom {
	def toSqlDmlRegexp(value: String, regex: String) =
		value + " ~ " + regex

	override def toSqlDdl(statement: DdlStorageStatement): SqlStatement =
		statement.action match {
			case StorageRenameColumn(tableName, oldName, column) =>
				SqlStatement(
					"ALTER TABLE " + tableName + " RENAME COLUMN " + oldName + " TO " + column.name,
					Map())
			case StorageRemoveIndex(tableName, name) =>
				SqlStatement(
					"DROP INDEX " + name,
					Map())
			case StorageRenameTable(oldName, newName) =>
				SqlStatement(
					"ALTER TABLE " + oldName + " RENAME TO " + newName,
					Map())
			case other =>
				super.toSqlDdl(statement)
		}

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
			case value: BooleanStorageValue =>
				"BOOLEAN"
			case value: StringStorageValue =>
				"VARCHAR(200)"
			case value: FloatStorageValue =>
				"DOUBLE PRECISION"
			case value: DateStorageValue =>
				"TIMESTAMP"
			case value: DoubleStorageValue =>
				"DOUBLE PRECISION"
			case value: BigDecimalStorageValue =>
				"DECIMAL"
			case value: ByteArrayStorageValue =>
				"BYTEA"
			case value: ReferenceStorageValue =>
				"VARCHAR(200)"
		}
}

object oracleDialect extends SqlIdiom {
	def toSqlDmlRegexp(value: String, regex: String) =
		"REGEXP_LIKE(" + value + ", " + regex + ")"

	override def toSqlDdl(statement: DdlStorageStatement): SqlStatement =
		statement.action match {
			case StorageRenameColumn(tableName, oldName, column) =>
				SqlStatement(
					"ALTER TABLE " + tableName + " RENAME COLUMN " + oldName + " TO " + column.name,
					Map())
			case StorageRemoveIndex(tableName, name) =>
				SqlStatement(
					"DROP INDEX " + name,
					Map())
			case StorageRenameTable(oldName, newName) =>
				SqlStatement(
					"ALTER TABLE " + oldName + " RENAME TO " + newName,
					Map())
			case other =>
				super.toSqlDdl(statement)
		}

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
			case value: BooleanStorageValue =>
				"NUMBER(1)"
			case value: StringStorageValue =>
				"VARCHAR2(200)"
			case value: FloatStorageValue =>
				"DOUBLE PRECISION"
			case value: DateStorageValue =>
				"TIMESTAMP"
			case value: DoubleStorageValue =>
				"DOUBLE PRECISION"
			case value: BigDecimalStorageValue =>
				"DECIMAL"
			case value: ByteArrayStorageValue =>
				"BLOB"
			case value: ReferenceStorageValue =>
				"VARCHAR2(200)"
		}

}
