package net.fwbrasil.activate.storage.relational

import java.util.regex._
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

	def toSqlStatement(statement: DmlStorageStatement): SqlStatement = {
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
		}
	}

	def toSqlQuery(statement: QueryStorageStatement): SqlStatement =
		toSqlQuery(statement.query)

	def toSqlQuery(query: Query[_]): SqlStatement = {
		implicit val binds = MutableMap[StorageValue, String]()

		SqlStatement("SELECT DISTINCT " + toSqlQuery(query.select) +
			" FROM " + toSqlQuery(query.from) + " WHERE " + toSqlQuery(query.where) + toSqlQueryOrderBy(query.orderByClause), (Map() ++ binds) map { _.swap })
	}

	def toSqlQuery(select: Select)(implicit binds: MutableMap[StorageValue, String]): String =
		(for (value <- select.values)
			yield toSqlQuerySelect(value)).mkString(", ");

	def toSqlQueryOrderBy(orderBy: Option[OrderBy])(implicit binds: MutableMap[StorageValue, String]): String = {
		if (orderBy.isDefined)
			" ORDER BY " + toSqlQuery(orderBy.get.criterias: _*)
		else ""
	}

	def toSqlQuery(criterias: OrderByCriteria[_]*)(implicit binds: MutableMap[StorageValue, String]): String =
		(for (criteria <- criterias)
			yield toSqlQuery(criteria)).mkString(", ")

	def toSqlQuery(criteria: OrderByCriteria[_])(implicit binds: MutableMap[StorageValue, String]): String =
		toSqlQuery(criteria.value) + " " + (
			if (criteria.direction ==
				orderByAscendingDirection)
				"asc"
			else
				"desc")

	def toSqlQuery(value: QueryValue)(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: QueryBooleanValue =>
				toSqlQuery(value)
			case value: QuerySelectValue[_] =>
				toSqlQuerySelect(value)
		}

	def toSqlQuerySelect(value: QuerySelectValue[_])(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: QueryEntityValue[_] =>
				toSqlQuery(value)
			case value: SimpleValue[_] =>
				toSqlQuery(value)
		}

	def toSqlQuery(value: SimpleValue[_])(implicit binds: MutableMap[StorageValue, String]): String =
		value.anyValue match {
			case null =>
				"is null"
			case other =>
				bind(Marshaller.marshalling(value.entityValue))
		}

	def toSqlQuery(value: QueryBooleanValue)(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: SimpleQueryBooleanValue =>
				bind(Marshaller.marshalling(value.entityValue))
			case value: Criteria =>
				toSqlQuery(value)
		}

	def toSqlQuery[V](value: QueryEntityValue[V])(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: QueryEntityInstanceValue[Entity] =>
				bind(StringStorageValue(Option(value.entityId)))
			case value: QueryEntitySourcePropertyValue[v] =>
				value.entitySource.name + "." + value.propertyPathNames.mkString(".")
			case value: QueryEntitySourceValue[v] =>
				value.entitySource.name + ".id"
		}

	def toSqlQuery(value: From)(implicit binds: MutableMap[StorageValue, String]): String =
		(for (source <- value.entitySources)
			yield toTableName(source.entityClass) + " " + source.name).mkString(", ")

	def toSqlQuery(value: Where)(implicit binds: MutableMap[StorageValue, String]): String =
		toSqlQuery(value.value)

	def toSqlQuery(value: Criteria)(implicit binds: MutableMap[StorageValue, String]): String =
		value match {
			case value: BooleanOperatorCriteria =>
				toSqlQuery(value.valueA) + toSqlQuery(value.operator) + toSqlQuery(value.valueB)
			case value: SimpleOperatorCriteria =>
				toSqlQuery(value.valueA) + toSqlQuery(value.operator)
			case value: CompositeOperatorCriteria =>
				toSqlQuery(value.valueA) + toSqlQuery(value.operator) + toSqlQuery(value.valueB)
		}

	def toSqlQuery(value: Operator)(implicit binds: MutableMap[StorageValue, String]): String =
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
}

object oracleDialect extends SqlIdiom
