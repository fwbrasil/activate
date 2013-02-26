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

case class JdbcStatementException(statement: JdbcStatement, exception: Exception, nextException: Exception)
    extends Exception("Statement exception: " + statement + ". Next exception: " + Option(nextException).map(_.getMessage), exception)

trait JdbcRelationalStorage extends RelationalStorage[Connection] with Logging {

    val dialect: SqlIdiom

    protected def getConnection: Connection

    override protected[activate] def prepareDatabase =
        dialect.prepareDatabase(this)

    def executeWithTransaction[R](f: (Connection) => R) = {
        val connection = getConnectionWithoutAutoCommit
        try {
            val res = f(connection)
            connection.commit
            res
        } catch {
            case e: Throwable =>
                connection.rollback
                throw e
        } finally
            connection.close
    }

    private def getConnectionWithoutAutoCommit = {
        val con = getConnection
        con.setAutoCommit(false)
        con
    }

    def directAccess =
        getConnectionWithoutAutoCommit

    override protected[activate] def executeStatements(storageStatements: List[StorageStatement]) = {
        val sqlStatements =
            storageStatements.map(dialect.toSqlStatement).flatten
        val batchStatements =
            BatchSqlStatement.group(sqlStatements)
        executeWithTransaction {
            connection =>
                for (batchStatement <- batchStatements)
                    execute(batchStatement, connection)
        }
    }

    private protected[activate] def satisfyRestriction(jdbcStatement: JdbcStatement) =
        jdbcStatement.restrictionQuery.map(tuple => {
            executeWithTransaction {
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

    protected[activate] def execute(jdbcStatement: JdbcStatement, connection: Connection) =
        try
            if (satisfyRestriction(jdbcStatement)) {
                val stmt = createPreparedStatement(jdbcStatement, connection, true)
                try {
                    val result = stmt.executeBatch
                    val expectedResult = jdbcStatement.expectedNumbersOfAffectedRowsOption
                    require(result.size == expectedResult.size)
                    for (i <- 0 until result.size) {
                        expectedResult(i).map { expected =>
                            require(result(i) == expected)
                        }
                    }
                } finally stmt.close
            }
        catch {
            case e: BatchUpdateException =>
                throw JdbcStatementException(jdbcStatement, e, e.getNextException)
        }

    protected[activate] def query(queryInstance: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] =
        executeQuery(dialect.toSqlDml(QueryStorageStatement(queryInstance)), expectedTypes)

    protected[activate] def executeQuery(sqlStatement: SqlStatement, expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
        executeWithTransaction {
            connection =>
                val stmt = createPreparedStatement(sqlStatement, connection, false)
                try {
                    val resultSet = stmt.executeQuery
                    try {
                        var result = List[List[StorageValue]]()
                        while (resultSet.next) {
                            var i = 0
                            result ::=
                                (for (expectedType <- expectedTypes) yield {
                                    i += 1
                                    dialect.getValue(resultSet, i, expectedType, connection)
                                })
                        }
                        result
                    } finally
                        resultSet.close
                } finally
                    stmt.close
        }
    }

    protected[activate] def createPreparedStatement(jdbcStatement: JdbcStatement, connection: Connection, isDml: Boolean) = {
        val (statement, bindsList) = jdbcStatement.toIndexedBind
        val ps =
            if (isDml)
                connection.prepareStatement(statement)
            else
                connection.prepareStatement(statement, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
        try {
            for (binds <- bindsList) {
                var i = 1
                for (bindValue <- binds) {
                    dialect.setValue(ps, i, bindValue)
                    i += 1
                }
                if (isDml)
                    ps.addBatch
            }
        } catch {
            case e: Throwable =>
                ps.close
                throw e
        }
        ps
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

    private var _connectionPool: BoneCP = _

    override def delayedInit(body: => Unit) = {
        body
        initConnectionPool
    }

    def connectionPool =
        _connectionPool

    override def reinitialize = {
        _connectionPool.close
        while (_connectionPool.getTotalLeased != 0)
            Thread.sleep(10)
        initConnectionPool
    }

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