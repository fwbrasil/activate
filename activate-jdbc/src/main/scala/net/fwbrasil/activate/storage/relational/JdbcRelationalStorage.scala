package net.fwbrasil.activate.storage.relational

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
import com.mchange.v2.c3p0.ComboPooledDataSource

trait JdbcRelationalStorage extends RelationalStorage[Connection] with Logging {

	val dialect: SqlIdiom

	protected def getConnection: Connection

	protected def executeWithConnection[R](f: (Connection) => R) = {
		val connection = getConnectionWithoutAutoCommit
		try f(connection)
		finally {
			connection.rollback
			connection.close
		}
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
			storageStatements.map(dialect.toSqlStatement)
		val batchStatements =
			BatchSqlStatement.group(sqlStatements)
		executeWithConnection {
			connection =>
				for (batchStatement <- batchStatements)
					execute(batchStatement, connection)
				connection.commit
		}
	}

	private protected[activate] def satisfyRestriction(jdbcStatement: JdbcStatement, connection: Connection) =
		jdbcStatement.restrictionQuery.map(tuple => {
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
		}).getOrElse(true)

	protected[activate] def execute(jdbcStatement: JdbcStatement, connection: Connection) =
		if (satisfyRestriction(jdbcStatement, connection)) {
			val stmt = createPreparedStatement(jdbcStatement, connection, true)
			try stmt.executeBatch
			finally stmt.close
		}

	protected[activate] def query(queryInstance: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] =
		executeQuery(dialect.toSqlDml(QueryStorageStatement(queryInstance)), expectedTypes)

	protected[activate] def executeQuery(sqlStatement: SqlStatement, expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
		executeWithConnection {
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
									dialect.getValue(resultSet, i, expectedType)
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
			case e =>
				ps.close
				throw e
		}
		ps
	}

}

trait SimpleJdbcRelationalStorage extends JdbcRelationalStorage {

	val jdbcDriver: String
	val url: String
	val user: String
	val password: String

	override def getConnection = {
		Class.forName(jdbcDriver)
		DriverManager.getConnection(url, user, password)
	}
}

trait PooledJdbcRelationalStorage extends JdbcRelationalStorage {

	val jdbcDriver: String
	val url: String
	val user: String
	val password: String

	lazy val dataSource = {
		val dataSource = new ComboPooledDataSource
		dataSource.setDriverClass(jdbcDriver)
		dataSource.setJdbcUrl(url)
		dataSource.setUser(user)
		dataSource.setPassword(password)
		dataSource
	}

	override def getConnection =
		dataSource.getConnection

}

object PooledJdbcRelationalStorageFactory extends StorageFactory {
	override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] = {
		new PooledJdbcRelationalStorage {
			val jdbcDriver = properties("jdbcDriver")
			val url = properties("url")
			val user = properties("user")
			val password = properties("password")
			val dialect = SqlIdiom.dialect(properties("dialect"))
		}
	}
}

object SimpleJdbcRelationalStorageFactory extends StorageFactory {
	override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] = {
		new SimpleJdbcRelationalStorage {
			val jdbcDriver = properties("jdbcDriver")
			val url = properties("url")
			val user = properties("user")
			val password = properties("password")
			val dialect = SqlIdiom.dialect(properties("dialect"))
		}
	}
}

trait DataSourceJdbcRelationalStorage extends JdbcRelationalStorage {

	val dataSourceName: String
	val initialContext = new InitialContext()
	val dataSource = initialContext.lookup(dataSourceName).asInstanceOf[DataSource]

	override def getConnection =
		dataSource.getConnection
}

object DataSourceJdbcRelationalStorageFactory extends StorageFactory {
	override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] = {
		new DataSourceJdbcRelationalStorage {
			val dataSourceName = properties("dataSourceName")
			val dialect = SqlIdiom.dialect(properties("dialect"))
		}
	}
}