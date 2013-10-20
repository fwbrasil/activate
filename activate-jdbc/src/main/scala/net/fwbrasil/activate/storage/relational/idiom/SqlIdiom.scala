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
import net.fwbrasil.activate.storage.relational.ModifyStorageStatement
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.storage.relational.NormalQlStatement
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
import net.fwbrasil.activate.storage.relational.StorageStatement
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.statement.Criteria
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
import net.fwbrasil.activate.statement.FunctionApply
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.statement.ToLowerCase
import net.fwbrasil.activate.storage.relational.InsertStorageStatement
import net.fwbrasil.activate.storage.relational.DeleteStorageStatement
import net.fwbrasil.activate.storage.relational.UpdateStorageStatement
import net.fwbrasil.activate.storage.marshalling.StorageModifyColumnType

object SqlIdiom {
    lazy val dialectsMap = {
        val dialects =
            Reflection
                .getAllImplementorsNames(
                    List(classOf[SqlIdiom]),
                    classOf[SqlIdiom])
                .map(classOf[SqlIdiom].getClassLoader.loadClass)
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

trait SqlIdiom extends QlIdiom {

    def supportsLimitedQueries = true

    def supportsRegex = true

    def prepareDatabase(storage: JdbcRelationalStorage) = {}

    def findTableStatement(tableName: String): String

    def findTableColumnStatement(tableName: String, columnName: String): String

    def findIndexStatement(tableName: String, indexName: String): String

    def findConstraintStatement(tableName: String, constraintName: String): String

    protected def setValue[V](ps: PreparedStatement, f: (V) => Unit, i: Int, optionValue: Option[V], sqlType: Int): Unit =
        if (optionValue.isEmpty || optionValue == null)
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

    def toSqlDdlAction(action: ModifyStorageAction): List[NormalQlStatement] =
        action match {
            case action: StorageCreateListTable =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findTableStatement(action.listTableName), action.ifNotExists))) ++
                    toSqlDdlAction(action.addOwnerIndexAction)
            case action: StorageRemoveListTable =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableStatement(action.listTableName), action.ifExists)))
            case action: StorageCreateTable =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findTableStatement(action.tableName), action.ifNotExists)))
            case action: StorageRenameTable =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableStatement(action.oldName), action.ifExists)))
            case action: StorageRemoveTable =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableStatement(action.name), action.ifExists)))
            case action: StorageAddColumn =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findTableColumnStatement(action.tableName, action.column.name), action.ifNotExists)))
            case action: StorageRenameColumn =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableColumnStatement(action.tableName, action.oldName), action.ifExists)))
            case action: StorageModifyColumnType =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableColumnStatement(action.tableName, action.column.name), action.ifExists)))
            case action: StorageRemoveColumn =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findTableColumnStatement(action.tableName, action.name), action.ifExists)))
            case action: StorageAddIndex =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findIndexStatement(action.tableName, action.indexName), action.ifNotExists)))
            case action: StorageRemoveIndex =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findIndexStatement(action.tableName, action.name), action.ifExists)))
            case action: StorageAddReference =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifNotExistsRestriction(findConstraintStatement(action.tableName, action.constraintName), action.ifNotExists)))
            case action: StorageRemoveReference =>
                List(new NormalQlStatement(
                    statement = toSqlDdl(action),
                    restrictionQuery = ifExistsRestriction(findConstraintStatement(action.tableName, action.constraintName), action.ifExists)))
        }

    def versionVerifyQueries(reads: Map[Class[Entity], List[(String, Long)]]) =
        for ((clazz, versions) <- reads if (versions.nonEmpty)) yield {
            val ids = versions.map(_._1)
            var binds = Map[String, StorageValue]()
            val conditions =
                for (i <- 0 until versions.size) yield {
                    val (id, version) = versions(i)
                    val condition = "(" + escape("id") + " = :id" + i + " and " + escape("version") + " is not null and " + escape("version") + " != :version" + i + ")"
                    binds += ("id" + i) -> new StringStorageValue(Some(id))
                    binds += ("version" + i) -> new LongStorageValue(Some(version))
                    condition
                }
            val query = "SELECT " + escape("id") + " FROM " + toTableName(clazz) + " WHERE " + conditions.mkString(" OR ")
            (new NormalQlStatement(query, binds), versions)
        }

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

