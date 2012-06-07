package net.fwbrasil.activate.storage.relational

import net.fwbrasil.activate.storage.marshalling.StorageValue
import scala.collection.mutable.ListBuffer
import java.util.regex.Pattern

trait JdbcStatement {
	val statement: String
	val restrictionQuery: Option[(String, Int)]
	val bindsList: List[Map[String, StorageValue]]

	def toIndexedBind = {
		val pattern = Pattern.compile("(:[a-zA-Z0-9_]*)")
		var matcher = pattern.matcher(statement)
		var result = statement
		matcher.matches
		val columns = ListBuffer[String]()
		while (matcher.find) {
			val group = matcher.group
			result = matcher.replaceFirst("?")
			matcher = pattern.matcher(result)
			columns += group.substring(1)
		}
		val valuesList =
			for (binds <- bindsList)
				yield columns.map(binds(_))
		(result, valuesList)
	}
}

class SqlStatement(
	val statement: String,
	val binds: Map[String, StorageValue],
	val restrictionQuery: Option[(String, Int)])
		extends JdbcStatement {

	def this(statement: String, restrictionQuery: Option[(String, Int)]) =
		this(statement, Map(), restrictionQuery)

	def this(statement: String) =
		this(statement, Map(), None)

	def this(statement: String, binds: Map[String, StorageValue]) =
		this(statement, binds, None)

	val bindsList = List(binds)

}

class BatchSqlStatement(
	val statement: String,
	val bindsList: List[Map[String, StorageValue]],
	val restrictionQuery: Option[(String, Int)])
		extends JdbcStatement

object BatchSqlStatement {
	def group(sqlStatements: List[SqlStatement]) = {
		val grouped = sqlStatements.groupBy(s => (s.statement, s.restrictionQuery))
		for (((statement, restrictionQuery), sqlStatements) <- grouped)
			yield new BatchSqlStatement(statement, sqlStatements.map(_.binds), restrictionQuery)
	}
}
