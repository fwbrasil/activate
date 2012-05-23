package net.fwbrasil.activate.storage.relational

import java.util.regex.Pattern
import net.fwbrasil.activate.statement.query._
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
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.statement.Operator
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.statement.StatementEntityValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.mass.UpdateAssignment
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.util.RichList._

trait JdbcStatement {
	val statement: String
	val restrictionQuery: Option[(String, Int)]
	val bindsList: List[Map[String, StorageValue]]

	def toIndexedBind = {
		val pattern = Pattern.compile("(:[a-zA-Z]*[0-9]*)")
		var matcher = pattern.matcher(statement)
		var result = statement
		matcher.matches
		val columns = ListBuffer[String]()
		while (matcher.find) {
			val group = matcher.group
			result = matcher.replaceFirst("?")
			matcher = pattern.matcher(result)
			columns += group.substring(1)
		}
		val valuesList =
			for (binds <- bindsList)
				yield columns.map(binds(_))
		(result, valuesList)
	}
}

class SqlStatement(
	val statement: String,
	val binds: Map[String, StorageValue],
	val restrictionQuery: Option[(String, Int)])
		extends JdbcStatement {

	def this(statement: String, restrictionQuery: Option[(String, Int)]) =
		this(statement, Map(), restrictionQuery)

	def this(statement: String) =
		this(statement, Map(), None)

	def this(statement: String, binds: Map[String, StorageValue]) =
		this(statement, binds, None)

	val bindsList = List(binds)

}

class BatchSqlStatement(
	val statement: String,
	val bindsList: List[Map[String, StorageValue]],
	val restrictionQuery: Option[(String, Int)])
		extends JdbcStatement

object BatchSqlStatement {
	def group(sqlStatements: List[SqlStatement]) = {
		val grouped = sqlStatements.groupBy(s => (s.statement, s.restrictionQuery))
		for (((statement, restrictionQuery), sqlStatements) <- grouped)
			yield new BatchSqlStatement(statement, sqlStatements.map(_.binds), restrictionQuery)
	}
}

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
		"	" + escape(column.name) + " " + toSqlDdl(column.storageValue)

	def escape(string: String): String

	def toSqlDml(statement: QueryStorageStatement): SqlStatement =
		toSqlDml(statement.query)

	def toSqlDml(query: Query[_]): SqlStatement = {
		implicit val binds = MutableMap[StorageValue, String]()
		new SqlStatement("SELECT DISTINCT " + toSqlDml(query.select) +
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
				value.entitySource.name + "." + value.propertyPathNames.mkString(".")
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

	def toSqlDdl(statement: StorageMigrationAction): String

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

	def toSqlDml(assignment: UpdateAssignment)(implicit binds: MutableMap[StorageValue, String]): String =
		toSqlDml(assignment.assignee) + " = " + toSqlDml(assignment.value)

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

	override def findTableStatement(tableName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.TABLES " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'"

	override def findTableColumnStatement(tableName: String, columnName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.COLUMNS " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'" +
			"   AND COLUMN_NAME = '" + columnName + "'"

	override def findIndexStatement(tableName: String, indexName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.STATISTICS " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'" +
			"   AND INDEX_NAME = '" + indexName + "'"

	override def findConstraintStatement(tableName: String, constraintName: String): String =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'" +
			"   AND CONSTRAINT_NAME = '" + constraintName + "'"

	override def escape(string: String) =
		"`" + string + "`"

	override def toSqlDdl(action: StorageMigrationAction): String = {
		action match {
			case StorageCreateTable(tableName, columns, ifNotExists) =>
				"CREATE TABLE " + escape(tableName) + "(\n" +
					"	ID " + toSqlDdl(StringStorageValue(None)) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
					columns.map(toSqlDdl).mkString(", \n") +
					")"
			case StorageRenameTable(oldName, newName, ifExists) =>
				"RENAME TABLE " + escape(oldName) + " TO " + escape(newName)
			case StorageRemoveTable(name, ifExists, isCascade) =>
				"DROP TABLE " + escape(name) + (if (isCascade) " CASCADE" else "")
			case StorageAddColumn(tableName, column, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
			case StorageRenameColumn(tableName, oldName, column, ifExists) =>
				"ALTER TABLE " + escape(tableName) + " CHANGE " + escape(oldName) + " " + toSqlDdl(column)
			case StorageRemoveColumn(tableName, name, ifExists) =>
				"ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
			case StorageAddIndex(tableName, columnName, indexName, ifNotExists) =>
				"CREATE INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + escape(columnName) + ")"
			case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
				"DROP INDEX " + escape(name) + " ON " + escape(tableName)
			case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
			case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " DROP CONSTRAINT " + escape(constraintName)
		}
	}

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
			case value: LongStorageValue =>
				"BIGINT"
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

	override def findTableStatement(tableName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.TABLES " +
			" WHERE TABLE_SCHEMA = CURRENT_SCHEMA " +
			"   AND TABLE_NAME = '" + tableName.toLowerCase + "'"

	override def findTableColumnStatement(tableName: String, columnName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.COLUMNS " +
			" WHERE TABLE_SCHEMA = CURRENT_SCHEMA " +
			"   AND TABLE_NAME = '" + tableName.toLowerCase + "'" +
			"   AND COLUMN_NAME = '" + columnName.toLowerCase + "'"

	override def findIndexStatement(tableName: String, indexName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.STATISTICS " +
			" WHERE TABLE_SCHEMA = CURRENT_SCHEMA " +
			"   AND TABLE_NAME = '" + tableName.toLowerCase + "'" +
			"   AND INDEX_NAME = '" + indexName.toLowerCase + "'"

	override def findConstraintStatement(tableName: String, constraintName: String): String =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
			" WHERE TABLE_SCHEMA = CURRENT_SCHEMA " +
			"   AND TABLE_NAME = '" + tableName.toLowerCase + "'" +
			"   AND CONSTRAINT_NAME = '" + constraintName.toLowerCase + "'"

	override def escape(string: String) =
		"\"" + string.toLowerCase + "\""

	override def toSqlDdl(action: StorageMigrationAction): String = {
		action match {
			case StorageCreateTable(tableName, columns, ifNotExists) =>
				"CREATE TABLE " + escape(tableName) + "(\n" +
					"	ID " + toSqlDdl(StringStorageValue(None)) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
					columns.map(toSqlDdl).mkString(", \n") +
					")"
			case StorageRenameTable(oldName, newName, ifExists) =>
				"ALTER TABLE " + escape(oldName) + " RENAME TO " + escape(newName)
			case StorageRemoveTable(name, ifExists, isCascade) =>
				println("DROP TABLE " + escape(name) + (if (isCascade) " CASCADE" else ""))
				"DROP TABLE " + escape(name) + (if (isCascade) " CASCADE" else "")
			case StorageAddColumn(tableName, column, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
			case StorageRenameColumn(tableName, oldName, column, ifExists) =>
				"ALTER TABLE " + escape(tableName) + " RENAME COLUMN " + escape(oldName) + " TO " + escape(column.name)
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

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
			case value: LongStorageValue =>
				"DECIMAL"
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

	override def findTableStatement(tableName: String) =
		"SELECT COUNT(1) " +
			"  FROM USER_TABLES " +
			" WHERE TABLE_NAME = '" + tableName.toUpperCase + "'"

	override def findTableColumnStatement(tableName: String, columnName: String) =
		"SELECT COUNT(1) " +
			"  FROM USER_TAB_COLUMNS " +
			" WHERE TABLE_NAME = '" + tableName.toUpperCase + "' " +
			"   AND COLUMN_NAME = '" + columnName.toUpperCase + "'"

	override def findIndexStatement(tableName: String, indexName: String) =
		"SELECT COUNT(1) " +
			"  FROM USER_INDEXES " +
			" WHERE INDEX_NAME = '" + indexName.toUpperCase + "'"

	override def findConstraintStatement(tableName: String, constraintName: String): String =
		"SELECT COUNT(1) " +
			"  FROM USER_CONSTRAINTS " +
			" WHERE TABLE_NAME = '" + tableName + "'" +
			"   AND CONSTRAINT_NAME = '" + constraintName + "'"

	override def escape(string: String) =
		"\"" + string.toUpperCase + "\""

	override def toSqlDdl(action: StorageMigrationAction): String = {
		action match {
			case StorageCreateTable(tableName, columns, ifNotExists) =>
				"CREATE TABLE " + escape(tableName) + "(\n" +
					"	ID " + toSqlDdl(StringStorageValue(None)) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
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

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
			case value: LongStorageValue =>
				"DECIMAL"
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
