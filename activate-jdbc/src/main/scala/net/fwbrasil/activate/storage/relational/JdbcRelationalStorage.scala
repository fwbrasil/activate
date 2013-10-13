package net.fwbrasil.activate.storage.relational

import language.existentials
import net.fwbrasil.scala.UnsafeLazy._
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import javax.naming.InitialContext
import javax.sql.DataSource
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom
import java.sql.BatchUpdateException
import com.jolbox.bonecp.BoneCP
import com.jolbox.bonecp.BoneCPConfig
import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.ActivateConcurrentTransactionException
import java.sql.PreparedStatement
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.storage.marshalling.ListStorageValue

case class JdbcStatementException(
    statement: QlStatement,
    exception: Exception,
    nextException: Exception)
        extends Exception(
            "Statement exception: " +
                statement +
                ". Next exception: " +
                Option(nextException).map(_.getMessage),
            exception)

trait JdbcRelationalStorage extends RelationalStorage[Connection] with Logging {

    val dialect: SqlIdiom
    val batchLimit = 1000
    private val preparedStatementCache = new PreparedStatementCache

    protected def getConnection: Connection

    override def supportsRegex =
        dialect.supportsRegex

    override def supportsLimitedQueries =
        dialect.supportsLimitedQueries

    override def reinitialize = {
        preparedStatementCache.clear
        super.reinitialize
    }

    override protected[activate] def prepareDatabase =
        dialect.prepareDatabase(this)

    private def getConnectionWithAutoCommit = {
        val con = getConnection
        con.setAutoCommit(true)
        con
    }

    private def getConnectionWithoutAutoCommit = {
        val con = getConnection
        con.setAutoCommit(false)
        con
    }

    def directAccess =
        getConnectionWithoutAutoCommit

    def isMemoryStorage = false
    def isSchemaless = false
    def isTransactional = true
    def supportsQueryJoin = true

    override protected[activate] def executeStatements(
        reads: Map[Class[Entity], List[(String, Long)]],
        storageStatements: List[StorageStatement]): Option[TransactionHandle] = {
        val sqlStatements =
            storageStatements.map(dialect.toSqlStatement).flatten
        val statements =
            BatchQlStatement
                .group(sqlStatements, batchLimit)
                .filter(satisfyRestriction)
        Some(executeWithTransactionAndReturnHandle {
            connection =>
                verifyReads(reads)
                for (statement <- statements)
                    execute(statement, connection)
        })
    }

    private def verifyReads(reads: Map[Class[Entity], List[(String, Long)]]) = {
        for ((stmt, expectedVersions) <- dialect.versionVerifyQueries(reads)) {
            val versionsFromDatabase =
                executeQuery(stmt, List(new StringStorageValue(None), new LongStorageValue(None))).map {
                    _ match {
                        case StringStorageValue(Some(id)) :: LongStorageValue(Some(version)) :: Nil =>
                            (id, version)
                        case StringStorageValue(Some(id)) :: LongStorageValue(None) :: Nil =>
                            (id, 1)
                    }
                }
            val inconsistentVersions = expectedVersions.toSet -- versionsFromDatabase
            if (inconsistentVersions.nonEmpty)
                throw new ActivateConcurrentTransactionException(inconsistentVersions.map(_._1), List())
        }
    }

    private protected[activate] def satisfyRestriction(jdbcStatement: QlStatement) =
        jdbcStatement.restrictionQuery.map(tuple => {
            executeWithTransaction(autoCommit = true) {
                connection =>
                    val (query, expected) = tuple
                    val stmt = connection.prepareStatement(query)
                    val result =
                        try {
                            val resultSet = stmt.executeQuery
                            try {
                                resultSet.next
                                resultSet.getInt(1)
                            } finally
                                resultSet.close
                        } finally
                            stmt.close
                    result == expected
            }
        }).getOrElse(true)

    def execute(jdbcStatement: QlStatement, connection: Connection) =
        try {
            val (stmt, columns) = acquirePreparedStatement(jdbcStatement, connection, true)
            try {
                val result = jdbcStatement match {
                    case normal: NormalQlStatement =>
                        Array(stmt.executeUpdate)
                    case batch: BatchQlStatement =>
                        stmt.executeBatch
                }
                verifyStaleData(jdbcStatement, result)

            } finally
                releasePreparedStatement(jdbcStatement, connection, stmt, columns)
        } catch {
            case e: BatchUpdateException =>
                throw JdbcStatementException(jdbcStatement, e, e.getNextException)
            case e: ActivateConcurrentTransactionException =>
                throw e
            case other: Exception =>
                throw JdbcStatementException(jdbcStatement, other, other)
        }

    protected[activate] def query(queryInstance: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] =
        executeQuery(dialect.toSqlDml(QueryStorageStatement(queryInstance, entitiesReadFromCache)), expectedTypes)

    protected[activate] def executeQuery(sqlStatement: NormalQlStatement, expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
        executeWithTransaction(autoCommit = true) {
            connection =>
                val (stmt, columns) = acquirePreparedStatement(sqlStatement, connection, false)
                try {
                    val resultSet = stmt.executeQuery
                    try {
                        var result = List[List[StorageValue]]()
                        while (resultSet.next) {
                            var i = 0
                            result ::=
                                (for (expectedType <- expectedTypes) yield {
                                    i += 1
                                    getValue(resultSet, i, expectedType, connection)
                                })
                        }
                        result
                    } finally
                        resultSet.close
                } finally
                    releasePreparedStatement(sqlStatement, connection, stmt, columns)
        }
    }

    private def getValue(resultSet: ResultSet, i: Int, expectedType: StorageValue, connection: Connection): StorageValue =
        expectedType match {
            case value: ListStorageValue =>
                loadList(resultSet, i, connection, value)
            case other =>
                dialect.getValue(resultSet, i, expectedType)
        }

    private def loadList(resultSet: ResultSet, i: Int, connection: Connection, value: ListStorageValue) = {
        val split = Option(resultSet.getString(i)).getOrElse("0").split('|')
        val notEmptyFlag = split.head
        val listOption =
            if (notEmptyFlag != "1")
                None
            else {
                val sql = split.tail.head
                val stmt = connection.createStatement
                val list = try {
                    val res = stmt.executeQuery(sql)
                    try {
                        val list = ListBuffer[StorageValue]()
                        while (res.next())
                            list += getValue(res, 1, value.emptyStorageValue, connection)
                        list.toList
                    } finally
                        res.close
                } finally
                    stmt.close
                Some(list)
            }
        ListStorageValue(listOption, value.emptyStorageValue)
    }

    private def releasePreparedStatement(jdbcStatement: QlStatement, connection: Connection, ps: PreparedStatement, columns: List[String]) =
        preparedStatementCache.release(connection, jdbcStatement, ps, columns)

    protected[activate] def acquirePreparedStatement(jdbcStatement: QlStatement, connection: Connection, readOnly: Boolean) = {
        val (ps, columns) = preparedStatementCache.acquireFor(connection, jdbcStatement, readOnly)
        val valuesList = jdbcStatement.valuesList(columns)
        try {
            for (binds <- valuesList) {
                var i = 1
                for (bindValue <- binds) {
                    dialect.setValue(ps, i, bindValue)
                    i += 1
                }
                if (readOnly && jdbcStatement.isInstanceOf[BatchQlStatement])
                    ps.addBatch
            }
        } catch {
            case e: Throwable =>
                ps.close
                throw e
        }
        (ps, columns)
    }

    def executeWithTransactionAndReturnHandle[R](f: (Connection) => R) = {
        val connection = getConnectionWithoutAutoCommit
        try {
            f(connection)
            new TransactionHandle(
                commitBlock = () => connection.commit,
                rollbackBlock = () => connection.rollback,
                finallyBlock = () => connection.close)
        } catch {
            case e: Throwable =>
                try connection.rollback
                finally connection.close
                throw e
        }
    }

    def executeWithTransaction[R](f: (Connection) => R): R =
        executeWithTransaction(false)(f)

    def executeWithTransaction[R](autoCommit: Boolean)(f: (Connection) => R) = {
        val connection =
            if (autoCommit)
                getConnectionWithAutoCommit
            else
                getConnectionWithoutAutoCommit
        try {
            val res = f(connection)
            if (!autoCommit)
                connection.commit
            res
        } catch {
            case e: Throwable =>
                if (!autoCommit)
                    connection.rollback
                throw e
        } finally
            connection.close
    }

    private def verifyStaleData(jdbcStatement: QlStatement, result: Array[Int]): Unit = {
        val expectedResult = jdbcStatement.expectedNumbersOfAffectedRowsOption
        require(result.size == expectedResult.size)
        val invalidIds =
            (for (i <- 0 until result.size) yield {
                expectedResult(i).filter(_ != result(i)).map(_ => i)
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

}

trait SimpleJdbcRelationalStorage extends JdbcRelationalStorage with DelayedInit {

    val jdbcDriver: String
    val url: String
    val user: String
    val password: String

    override def delayedInit(body: => Unit) {
        body
        Class.forName(jdbcDriver)
    }

    override def getConnection =
        DriverManager.getConnection(url, user, password)
}

trait PooledJdbcRelationalStorage extends JdbcRelationalStorage with DelayedInit {

    val jdbcDriver: String
    val url: String
    val user: String
    val password: String

    val poolSize = 20

    val logStatements = false

    private var _connectionPool: BoneCP = _

    override def delayedInit(body: => Unit) = {
        body
        initConnectionPool
    }

    def connectionPool =
        _connectionPool

    override def getConnection =
        _connectionPool.getConnection

    private def initConnectionPool = {
        Class.forName(jdbcDriver)
        val config = new BoneCPConfig
        config.setJdbcUrl(url)
        config.setUsername(user)
        config.setPassword(password)
        config.setLazyInit(true)
        config.setDisableConnectionTracking(true)
        config.setReleaseHelperThreads(0)
        val partitions = Runtime.getRuntime.availableProcessors
        config.setPartitionCount(partitions)
        config.setMaxConnectionsPerPartition(poolSize / partitions)
        config.setLogStatementsEnabled(logStatements)
        _connectionPool = new BoneCP(config)
    }

}

trait DataSourceJdbcRelationalStorage extends JdbcRelationalStorage {

    val dataSourceName: String
    val initialContext = new InitialContext()
    val dataSource = initialContext.lookup(dataSourceName).asInstanceOf[DataSource]

    override def getConnection =
        dataSource.getConnection
}

object PooledJdbcRelationalStorageFactory extends StorageFactory {
    class PooledJdbcRelationalStorageFromFactory(val properties: Map[String, String]) extends PooledJdbcRelationalStorage {
        val jdbcDriver = properties("jdbcDriver")
        val url = properties("url")
        val user = properties("user")
        val password = properties("password")
        val dialect = SqlIdiom.dialect(properties("dialect"))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new PooledJdbcRelationalStorageFromFactory(properties)
}

object SimpleJdbcRelationalStorageFactory extends StorageFactory {
    class SimpleJdbcRelationalStorageFromFactory(val properties: Map[String, String]) extends SimpleJdbcRelationalStorage {
        val jdbcDriver = properties("jdbcDriver")
        val url = properties("url")
        val user = properties("user")
        val password = properties("password")
        val dialect = SqlIdiom.dialect(properties("dialect"))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new SimpleJdbcRelationalStorageFromFactory(properties)
}

object DataSourceJdbcRelationalStorageFactory extends StorageFactory {
    class DataSourceJdbcRelationalStorageFromFactory(val properties: Map[String, String]) extends DataSourceJdbcRelationalStorage {
        val dataSourceName = properties("dataSourceName")
        val dialect = SqlIdiom.dialect(properties("dialect"))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new DataSourceJdbcRelationalStorageFromFactory(properties)
}