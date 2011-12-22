package net.fwbrasil.activate.storage.relational

import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.query.Query

import java.util.Date
import java.sql.Connection
import java.sql.ResultSet
import java.sql.DriverManager
import net.fwbrasil.activate.serialization.Serializator
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.storage.marshalling._
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Types

trait JdbcRelationalStorage extends RelationalStorage with Logging {
	
	val dialect: SqlIdiom
	implicit val serializator: Serializator
	
	def getConnection: Connection
	
	override def execute(storageStatements: List[DmlStorageStatement]) = {
		val sqlStatements = 
			for(storageStatement <- storageStatements) 
				yield dialect.toSqlStatement(storageStatement)
		val connection = getConnection
		try {
			for(sqlStatement <- sqlStatements)
				execute(sqlStatement, connection)
			connection.commit
		} catch {
			case ex =>
				connection.rollback
				throw ex
		}
	}
	
	def execute(sqlStatement: SqlStatement, connection: Connection) =
		createPreparedStatement(sqlStatement, connection, true).executeUpdate
	
	def query(queryInstance: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] = 
		executeQuery(dialect.toSqlQuery(QueryStorageStatement(queryInstance)), expectedTypes)

	def executeQuery(sqlStatement: SqlStatement, expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
		val stmt = createPreparedStatement(sqlStatement, getConnection, false)
		val resultSet = stmt.executeQuery
		var result = List[List[StorageValue]]()
		while(resultSet.next) {
			var i = 0
			result ::= 
				(for(expectedType <- expectedTypes)
					yield {
						i += 1
						dialect.getValue(resultSet, i, expectedType)
					})
		}
		stmt.close
		result
	}
	
	def createPreparedStatement(sqlStatement: SqlStatement, connection: Connection, isDml: Boolean) = {
		val (statement, binds) = sqlStatement.toIndexedBind
		info("Prepared statement: " + statement)
		val ps = 
			if(isDml)
				connection.prepareStatement(statement)
			else
				connection.prepareStatement(statement, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
		var i = 1
		for(bindValue <- binds) {
			dialect.setValue(ps, i, bindValue)
			i += 1
		}
		ps
	}
}

trait SimpleJdbcRelationalStorage extends JdbcRelationalStorage {
	
	val jdbcDriver: String
	val url: String
	val user: String
	val password: String
	
	private[this] lazy val connection = {
		Class.forName(jdbcDriver)
		val con = DriverManager.getConnection(url, user, password)
		con.setAutoCommit(false);
		con
	}
	
	override def getConnection = 
		connection
}

trait DataSourceJdbcRelationalStorage extends JdbcRelationalStorage {
	
	val dataSourceName: String
	val initialContext = new InitialContext()
	val dataSource = initialContext.lookup(dataSourceName).asInstanceOf[DataSource]
	
	override def getConnection = {
		val con = dataSource.getConnection
		con.setAutoCommit(false)
		con
	}
}