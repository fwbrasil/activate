package net.fwbrasil.thor.business

import net.fwbrasil.thor.thorContext._
import scala.collection.mutable.MutableList
import java.sql.DriverManager
import java.sql.ResultSet

class SqlQuerySource(
		var sql: String,
		var jdbcConnection: JdbcConnection) extends Source {
	var tupleTemplate = TupleTemplate(jdbcConnection.columns(sql): _*)
	def extract: TupleCollection =
		TupleCollection(tupleTemplate, jdbcConnection.executeSql(sql, tupleTemplate.columns.map(_.name)))
}

class JdbcConnection(
		var jdbcDriver: String,
		var url: String,
		var user: String,
		var password: String) extends Entity {

	private[this] def connection = {
		Class.forName(jdbcDriver)
		val con = DriverManager.getConnection(url, user, password)
		con.setAutoCommit(false);
		con
	}

	private[this] def query(sql: String) = {
		val conn = connection
		val stmt = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)
		stmt.executeQuery()
	}

	def executeSql(sql: String, columns: List[String]) = {
		val resultSet = query(sql)
		val result = MutableList[List[String]]()
		while (resultSet.next)
			result +=
				(for (col <- columns)
					yield resultSet.getString(col)).toList
		result.toList
	}

	def columns(sql: String) = {
		val resultSet = query(sql)
		val metadata = resultSet.getMetaData
		val count = metadata.getColumnCount
		for (i <- 1 to count)
			yield metadata.getColumnName(i)
	}
}