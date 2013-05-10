package net.fwbrasil.activate.async.postgresql

import com.github.mauricio.async.db.postgresql.util.URLParser
import com.github.mauricio.async.db.{ RowData, QueryResult, Connection }
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import net.fwbrasil.activate.storage.relational.RelationalStorage
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.relational.StorageStatement
import com.github.mauricio.async.db.pool.ConnectionPool
import com.github.mauricio.async.db.postgresql.pool.ConnectionObjectFactory
import com.github.mauricio.async.db.pool.PoolConfiguration
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.storage.relational.QueryStorageStatement
import net.fwbrasil.activate.storage.relational.JdbcStatement
import net.fwbrasil.activate.storage.relational.idiom.ActivateResultSet
import java.sql.Timestamp
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.storage.TransactionHandle
import scala.concurrent.Promise
import net.fwbrasil.activate.storage.relational.SqlStatement
import net.fwbrasil.activate.storage.relational.BatchSqlStatement
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.ActivateContext
import scala.concurrent.ExecutionContext.Implicits.global
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.codec.PostgreSQLConnectionHandler
import net.fwbrasil.activate.migration.Migration
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection

object context extends ActivateContext {
    val storage = new AsyncPostgresqlStorage {}
    override protected val runMigrationAtStartup = false
}
import context._

class AnEntity(var i: Int) extends Entity

class CreateSchema extends Migration()(context) {
    def timestamp = System.currentTimeMillis
    def up =
        table[AnEntity].createTable(_.column[Int]("i")).ifNotExists
}

object Main extends App {
    import context._
    val entity =
        transactional {
            new AnEntity(0)
        }
    transactional {
        entity.i += 1
        println(entity)
    }
    transactional {
        entity.i += 1
        println(entity)
    }
}

trait AsyncPostgresqlStorage extends RelationalStorage[Connection] {

    private val configuration = new Configuration(
        username = "postgres",
        host = "localhost",
        password = Some("postgres"),
        database = Some("activate_test"))
    private val factory = new ConnectionObjectFactory(configuration)
    private val pool = new ConnectionPool(factory, PoolConfiguration.Default)
    private val dialect = postgresqlDialect

    override protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[Entity]]) = {

        val jdbcStatement = dialect.toSqlDml(QueryStorageStatement(query, entitiesReadFromCache))
        val resultSetFuture = sendPreparedStatement(jdbcStatement, pool)
        val result =
            resultSetFuture.map(
                _.rows match {
                    case Some(resultSet) =>
                        resultSet.map {
                            row =>
                                val activateRS = AsyncPostgresqlResultSet(row)
                                var i = 0
                                val list = ListBuffer[StorageValue]()
                                for (storageValue <- expectedTypes) {
                                    val value = row(i)
                                    // TODO Review list support (connection null)!
                                    list += dialect.getValue(activateRS, i, storageValue, null)
                                    i += 1
                                }
                                list.toList
                        }.toList
                    case None =>
                        throw new IllegalStateException("Empty result.")
                })
        Await.result(result, 9999 seconds)
    }

    override protected[activate] def executeStatements(
        sqls: List[StorageStatement]) = {
        val sqlStatements =
            sqls.map(dialect.toSqlStatement).flatten
        val res =
            executeWithTransactionAndReturnHandle {
                connection =>
                    sqlStatements.foldLeft(Future[Unit]())((future, statement) => future.flatMap(_ => execute(statement, connection)))
            }
        Some(Await.result(res, 9999 seconds))
    }
    
    def execute(jdbcStatement: JdbcStatement, connection: Connection) =
        satisfyRestriction(jdbcStatement).flatMap { satisfy =>
            if (satisfy)
                jdbcStatement match {
                    case normal: SqlStatement =>
                        sendPreparedStatement(jdbcStatement, connection)
                            .map {
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
            pool.sendPreparedStatement(query).map {
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

    def executeWithTransactionAndReturnHandle(f: (PostgreSQLConnection) => Future[Unit]) = {
        pool.take.flatMap {
            connection =>
                f(connection).map { _ =>
                    new TransactionHandle(
                        commitBlock = () => commit(connection),
                        rollbackBlock = () => rollback(connection),
                        finallyBlock = () => pool.giveBack(connection))
                }
            //                .onFailure {
            //                    case e: Throwable =>
            //                        try rollback(connection)
            //                        finally pool.giveBack(connection)
            //                        throw e
            //                }
        }
    }

    private def commit(c: PostgreSQLConnection) =
        c.sendQuery("COMMIT")

    private def rollback(c: PostgreSQLConnection) =
        c.sendQuery("ROLLBACK")

    def directAccess = pool

    def isMemoryStorage = false
    def isSchemaless = false
    def isTransactional = true
    def supportsQueryJoin = true

    private def sendPreparedStatement(jdbcStatement: JdbcStatement, connection: Connection) =
        connection.sendPreparedStatement(
            jdbcStatement.indexedStatement,
            jdbcStatement.valuesList.head.map(_.value.getOrElse(null)))

}

case class AsyncPostgresqlResultSet(rowData: RowData)
        extends ActivateResultSet {
    private def value[T](i: Int) =
        Option(rowData(i).asInstanceOf[T])
    def getString(i: Int) =
        value[String](i)
    def getBytes(i: Int) =
        value[Array[Byte]](i)
    def getInt(i: Int) =
        value[Int](i)
    def getBoolean(i: Int) =
        value[Boolean](i)
    def getFloat(i: Int) =
        value[Float](i)
    def getLong(i: Int) =
        value[Long](i)
    def getTimestamp(i: Int) =
        value[Timestamp](i)
    def getDouble(i: Int) =
        value[Double](i)
    def getBigDecimal(i: Int) =
        value[java.math.BigDecimal](i)
}