package net.fwbrasil.activate.storage.relational.async

import com.github.mauricio.async.db.{ RowData, QueryResult, Connection }
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import net.fwbrasil.activate.storage.relational.RelationalStorage
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.relational.StorageStatement
import com.github.mauricio.async.db.pool.ConnectionPool
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import com.github.mauricio.async.db.pool.PoolConfiguration
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.storage.relational.QueryStorageStatement
import net.fwbrasil.activate.storage.relational.JdbcStatement
import net.fwbrasil.activate.storage.relational.idiom.ActivateResultSet
import java.sql.Timestamp
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.storage.TransactionHandle
import scala.concurrent.Promise
import scala.concurrent._
import net.fwbrasil.activate.storage.relational.SqlStatement
import net.fwbrasil.activate.storage.relational.BatchSqlStatement
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.ActivateContext
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.mauricio.async.db.Configuration
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.storage.relational.ModifyStorageStatement
import net.fwbrasil.activate.storage.relational.DdlStorageStatement
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import org.joda.time.DateTime
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import com.github.mauricio.async.db.mysql.pool.MySQLConnectionFactory
import com.github.mauricio.async.db.pool.ObjectFactory
import org.jboss.netty.util.CharsetUtil
import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom

trait JdbcRelationalAsyncStorage[C <: Connection] extends RelationalStorage[Future[C]] {

    def objectFactory: ObjectFactory[C]
    def charset = CharsetUtil.UTF_8
    private val pool = new ConnectionPool(objectFactory, PoolConfiguration.Default)
    val dialect: SqlIdiom

    override protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[Entity]]) = {
    	val result = queryAsync(query, expectedTypes, entitiesReadFromCache)
        Await.result(result, Duration.Inf)
    }

    override protected[activate] def queryAsync(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): Future[List[List[StorageValue]]] = {
        val jdbcStatement = dialect.toSqlDml(QueryStorageStatement(query, entitiesReadFromCache))
        println(jdbcStatement.statement)
        val resultSetFuture = sendPreparedStatement(jdbcStatement, pool)
        resultSetFuture.map(
            _.rows match {
                case Some(resultSet) =>
                    resultSet.map {
                        row =>
                            val rs = JdbcRelationalAsyncResultSet(row, charset.name)
                            var i = 0
                            val list = ListBuffer[StorageValue]()
                            for (storageValue <- expectedTypes) {
                                list += getValue(rs, i, storageValue)
                                i += 1
                            }
                            list.toList
                    }.toList
                case None =>
                    throw new IllegalStateException("Empty result.")
            })
    }

    private def getValue(rs: JdbcRelationalAsyncResultSet, i: Int, expectedType: StorageValue): StorageValue = {
        try expectedType match {
            case value: ListStorageValue =>
                loadList(rs, i, value)
            case other =>
                dialect.getValue(rs, i, other)
        } catch {
            case e: ArrayIndexOutOfBoundsException =>
                throw e
        }
    }

    private def loadList(rs: JdbcRelationalAsyncResultSet, i: Int, expectedType: ListStorageValue) = {
        // TODO review. It should be async too!
        val split = rs.getString(i).get.split('|')
        val notEmptyFlag = split.head
        val listOption =
            if (notEmptyFlag != "1")
                None
            else {
                val sql = split.tail.head
                val listFuture =
                    pool.sendQuery(sql).map {
                        _.rows match {
                            case Some(resultSet) =>
                                resultSet.map {
                                    row =>
                                        val rs = JdbcRelationalAsyncResultSet(row, charset.name)
                                        getValue(rs, 0, expectedType.emptyStorageValue)
                                }.toList
                            case None =>
                                throw new IllegalStateException("Empty result.")
                        }
                    }
                Some(Await.result(listFuture, Duration.Inf))
            }
        ListStorageValue(listOption, expectedType.emptyStorageValue)
    }

    override protected[activate] def executeStatements(
        sqls: List[StorageStatement]) = {
        val isDdl = sqls.find(_.isInstanceOf[DdlStorageStatement]).isDefined
        val sqlStatements =
            sqls.map(dialect.toSqlStatement).flatten
        val res =
            executeWithTransactionAndReturnHandle {
                connection =>
                    sqlStatements.foldLeft(Future[Unit]())((future, statement) => future.flatMap(_ => execute(statement, connection, isDdl)))
            }
        println("wait stmt resp")
        val res2 = Some(Await.result(res, Duration.Inf))
        println("received resp")
        res2
    }

    def execute(jdbcStatement: JdbcStatement, connection: Connection, isDdl: Boolean) =
        satisfyRestriction(jdbcStatement).flatMap { satisfy =>
            if (satisfy)
                jdbcStatement match {
                    case normal: SqlStatement =>
                        println(jdbcStatement.statement)
                        val future =
                            if (isDdl)
                                connection.sendQuery(jdbcStatement.statement)
                            else
                                sendPreparedStatement(jdbcStatement, connection)
                        future.map {
                            queryResult =>
                                verifyStaleData(
                                    jdbcStatement,
                                    Array(queryResult.rowsAffected))
                        }
                    case batch: BatchSqlStatement =>
                        throw new UnsupportedOperationException()
                }
            else
                Future({})
        }

    private def verifyStaleData(jdbcStatement: JdbcStatement, result: Array[Long]): Unit = {
        val expectedResult = jdbcStatement.expectedNumbersOfAffectedRowsOption
        require(result.size == expectedResult.size)
        val invalidIds =
            (for (i <- 0 until result.size) yield {
                expectedResult(i).filter(_ != result(i).intValue).map(_ => i)
            }).flatten
                .flatMap(jdbcStatement.bindsList(_).get("id"))
                .collect {
                    case StringStorageValue(Some(value: String)) =>
                        value
                    case ReferenceStorageValue(Some(value: String)) =>
                        value
                }
        if (invalidIds.nonEmpty)
            staleDataException(invalidIds.toSet)
    }

    private protected[activate] def satisfyRestriction(jdbcStatement: JdbcStatement) =
        jdbcStatement.restrictionQuery.map(tuple => {
            val (query, expected) = tuple
            println(query)
            pool.sendQuery(query).map {
                _.rows match {
                    case Some(resultSet) =>
                        resultSet(0)(0).asInstanceOf[Long]
                    case None =>
                        throw new IllegalStateException("Empty result")
                }
            }.map {
                _ == expected
            }
        }).getOrElse(Future(true))

    def executeWithTransactionAndReturnHandle(f: (Connection) => Future[Unit]) = {
        pool.take.flatMap {
            connection =>
                val res =
                    f(connection).map { _ =>
                        new TransactionHandle(
                            commitBlock = () => commit(connection),
                            rollbackBlock = () => rollback(connection),
                            finallyBlock = () => pool.giveBack(connection))
                    }
                res.onFailure {
                    case e: Throwable =>
                        try rollback(connection)
                        finally pool.giveBack(connection)
                        throw e
                }
                res
        }
    }

    private def commit(c: Connection) =
        Await.result(c.sendQuery("COMMIT"), Duration.Inf)

    private def rollback(c: Connection) =
        Await.result(c.sendQuery("ROLLBACK"), Duration.Inf)

    def directAccess = pool.take

    def isMemoryStorage = false
    def isSchemaless = false
    def isTransactional = true
    def supportsQueryJoin = true
    override def supportsAsync = true

    private def sendPreparedStatement(jdbcStatement: JdbcStatement, connection: Connection) =
        connection.sendPreparedStatement(
            jdbcStatement.indexedStatement,
            jdbcStatement.valuesList.head.map(toValue))

    private def toValue(storageValue: StorageValue) =
        storageValue match {
            case value: ListStorageValue =>
                if (value.value.isDefined)
                    "1"
                else
                    "0"
            case other =>
                other.value.getOrElse(null)
        }

}

case class JdbcRelationalAsyncResultSet(rowData: RowData, charset: String)
        extends ActivateResultSet {

    def getString(i: Int) =
        value[String](i)
    def getBytes(i: Int) =
        value[Array[Byte]](i)
    def getInt(i: Int) =
        valueCase[Int](i) {
            case n =>
                n.toString.toInt
        }
    def getBoolean(i: Int) =
        value[Boolean](i)
    def getFloat(i: Int) =
        valueCase[Float](i) {
            case n =>
                n.toString.toFloat
        }
    def getLong(i: Int) =
        valueCase[Long](i) {
            case n =>
                n.toString.toLong
        }
    def getTimestamp(i: Int) =
        valueCase[Timestamp](i) {
            case dateTime: DateTime =>
                new Timestamp(dateTime.getMillis)
        }
    def getDouble(i: Int) =
        valueCase[Double](i) {
            case n =>
                n.toString.toDouble
        }
    def getBigDecimal(i: Int) =
        valueCase[java.math.BigDecimal](i) {
            case n: BigDecimal =>
                n.bigDecimal
        }

    private def value[T](i: Int): Option[T] =
        Option(rowData(i).asInstanceOf[T])

    private def valueCase[T](i: Int)(f: PartialFunction[Any, T]): Option[T] = {
        val value = rowData(i)
        if (value == null)
            None
        else if (!f.isDefinedAt(value))
            throw new IllegalStateException("Invalid value")
        else
            Option(f(value))
    }
}