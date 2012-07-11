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
	override def toString = statement + restrictionQuery.map(" restriction " + _).getOrElse("")
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

	def isCompatible(other: SqlStatement) =
		statement == other.statement &&
			restrictionQuery == other.restrictionQuery

}

class BatchSqlStatement(
	val statement: String,
	val bindsList: List[Map[String, StorageValue]],
	val restrictionQuery: Option[(String, Int)])
		extends JdbcStatement 
		
object BatchSqlStatement {
	def group(sqlStatements: List[SqlStatement]): List[JdbcStatement] = {
		sqlStatements match {
			case Nil =>
				List()
			case head :: tail =>
				val (tailToGroup, others) = tail.span(_.isCompatible(head))
				val toGroup = List(head) ++ tailToGroup
				List(new BatchSqlStatement(head.statement, toGroup.map(_.binds), head.restrictionQuery)) ++ group(others)
		}
	}
}
