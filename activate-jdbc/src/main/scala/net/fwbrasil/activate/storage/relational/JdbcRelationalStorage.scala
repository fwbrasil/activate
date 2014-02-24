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
import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.ActivateConcurrentTransactionException
import java.sql.PreparedStatement
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import com.zaxxer.hikari.{HikariDataSource, HikariConfig}

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
    val queryLimit = 1000
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

    def getConnectionReadOnly = {
        val con = getConnection
        con.setAutoCommit(true)
        con.setReadOnly(true)
        con
    }

    def getConnectionReadWrite = {
        val con = getConnection
        con.setAutoCommit(false)
        con.setReadOnly(false)
        con
    }

    def directAccess =
        getConnectionReadWrite

    def isMemoryStorage = false
    def isSchemaless = false
    def isTransactional = true
    def supportsQueryJoin = true

    override protected[activate] def executeStatements(
        reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]],
        storageStatements: List[StorageStatement]): Option[TransactionHandle] = {
        verifyReads(reads)
        if (storageStatements.isEmpty)
            None
        else {
            val sqlStatements =
                storageStatements.map(dialect.toSqlStatement).flatten
            val statements =
                BatchQlStatement
                    .group(sqlStatements, batchLimit)
                    .filter(satisfyRestriction)
            verifyReads(reads)

            Some(executeWithTransactionAndReturnHandle {
                connection =>
                    for (statement <- statements)
                        execute(statement, connection)
            })
        }
    }

    private def verifyReads(reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]]) = {
        val inconsistentVersions =
            (for ((stmt, referenceStorageValue, clazz) <- dialect.versionVerifyQueries(reads, queryLimit)) yield {
                executeQuery(stmt, List(referenceStorageValue)).map {
                    _ match {
                        case List(ReferenceStorageValue(storageValue)) =>
                            (storageValue.value.get.asInstanceOf[BaseEntity#ID], clazz)
                        case other =>
                            throw new IllegalStateException("Invalid version information")
                    }
                }
            }).flatten
        if (inconsistentVersions.nonEmpty)
            staleDataException(inconsistentVersions.toSet)
    }

    private protected[activate] def satisfyRestriction(jdbcStatement: QlStatement) =
        jdbcStatement.restrictionQuery.map(tuple => {
            executeWithTransaction(readOnly = true) {
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

    protected[activate] def query(queryInstance: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[BaseEntity]]): List[List[StorageValue]] =
        executeQuery(dialect.toSqlDml(QueryStorageStatement(queryInstance, entitiesReadFromCache)), expectedTypes)

    protected[activate] def executeQuery(sqlStatement: NormalQlStatement, expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
        executeWithTransaction(readOnly = true) {
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
        val connection = getConnectionReadWrite
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

    def executeWithTransaction[R](readOnly: Boolean)(f: (Connection) => R) = {
        val connection =
            if (readOnly)
                getConnectionReadOnly
            else
                getConnectionReadWrite
        try {
            val res = f(connection)
            if (!readOnly)
                connection.commit
            res
        } catch {
            case e: Throwable =>
                if (!readOnly)
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
                        (value, jdbcStatement.entityClass)
                    case ReferenceStorageValue(value) =>
                        (value.value.get, jdbcStatement.entityClass)
                }
        if (invalidIds.nonEmpty)
            staleDataException(invalidIds.toSet.asInstanceOf[Set[(BaseEntity#ID, Class[BaseEntity])]])
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
    val jdbcDataSource: Option[String] = None
    val url: String
    val user: Option[String]
    val password: Option[String]

    val poolSize = 20

    val logStatements = false

    private var _connectionPool: DataSource = _

    override def delayedInit(body: => Unit) = {
        body
        val jdbcDataSourceName: String = jdbcDataSource match {
          case Some(name) => name
          case None => jdbcDriverToDataSource.get(jdbcDriver) match {
            case Some(name) => name
            case None => throw new Exception("Could not find provided jdbcDriver, please provide a jdbcDataSource instead.")
          }
        }
        initConnectionPool(jdbcDataSourceName)
    }

    def connectionPool =
        _connectionPool

    override def getConnection =
        _connectionPool.getConnection

    private def initConnectionPool(jdbcDataSourceName: String) = {
        ActivateContext.loadClass(jdbcDataSourceName)
        _connectionPool = new HikariDataSource(dialect.hikariConfigFor(this, jdbcDataSourceName))
    }

    override def getConnectionReadOnly = {
        val con = getConnection // autocommit by default 
        con.setReadOnly(true)
        con
    }

    private val jdbcDriverToDataSource = Map(
      "org.postgresql.Driver" -> "org.postgresql.ds.PGSimpleDataSource",
      "com.mysql.jdbc.Driver" -> "com.mysql.jdbc.jdbc2.optional.MysqlDataSource",
      "oracle.jdbc.driver.OracleDriver" -> "oracle.jdbc.pool.OracleDataSource",
      "org.h2.Driver" -> "org.h2.jdbcx.JdbcDataSource",
      "org.apache.derby.jdbc.EmbeddedDriver" -> "org.apache.derby.jdbc.EmbeddedDataSource",
      "org.hsqldb.jdbcDriver" -> "org.hsqldb.jdbc.JDBCDataSource",
      "com.ibm.db2.jcc.DB2Driver" -> "com.ibm.db2.jcc.DB2SimpleDataSource",
      "net.sourceforge.jtds.jdbc.Driver" -> "net.sourceforge.jtds.jdbcx.JtdsDataSource"
    )
}

trait DataSourceJdbcRelationalStorage extends JdbcRelationalStorage {

    val dataSourceName: String
    val initialContext = new InitialContext()
    val dataSource = initialContext.lookup(dataSourceName).asInstanceOf[DataSource]

    override def getConnection =
        dataSource.getConnection
}

object PooledJdbcRelationalStorageFactory extends StorageFactory {
    class PooledJdbcRelationalStorageFromFactory(val getProperty: String => Option[String]) extends PooledJdbcRelationalStorage {
        val jdbcDriver = getProperty("jdbcDriver").getOrElse("")
        override val jdbcDataSource = getProperty("jdbcDataSource")
        val url = getProperty("url").get
        val user = getProperty("user")
        val password = getProperty("password")
        val dialect = SqlIdiom.dialect(getProperty("dialect").get)
        getProperty("SQL2003") map { p => p match {
          case "true" => dialect.SQL2003 = true
          case _ =>
        }}
    }
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new PooledJdbcRelationalStorageFromFactory(getProperty)
}

object SimpleJdbcRelationalStorageFactory extends StorageFactory {
    class SimpleJdbcRelationalStorageFromFactory(val getProperty: String => Option[String]) extends SimpleJdbcRelationalStorage {
        val jdbcDriver = getProperty("jdbcDriver").get
        val url = getProperty("url").get
        val user = getProperty("user").get
        val password = getProperty("password").get
        val dialect = SqlIdiom.dialect(getProperty("dialect").get)
        getProperty("SQL2003") map { p => p match {
          case "true" => dialect.SQL2003 = true
          case _ =>
        }}
    }
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new SimpleJdbcRelationalStorageFromFactory(getProperty)
}

object DataSourceJdbcRelationalStorageFactory extends StorageFactory {
    class DataSourceJdbcRelationalStorageFromFactory(val getProperty: String => Option[String]) extends DataSourceJdbcRelationalStorage {
        val dataSourceName = getProperty("jdbcDataSource").get
        val dialect = SqlIdiom.dialect(getProperty("dialect").get)
        getProperty("SQL2003") map { p => p match {
          case "true" => dialect.SQL2003 = true
          case _ =>
        }}
    }
    override def buildStorage(getProperty: String => Option[String])(implicit context: ActivateContext): Storage[_] =
        new DataSourceJdbcRelationalStorageFromFactory(getProperty)
}