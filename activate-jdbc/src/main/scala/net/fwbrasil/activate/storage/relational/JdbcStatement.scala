package net.fwbrasil.activate.storage.relational

import java.util.regex.Pattern

import scala.collection.mutable.ListBuffer
import scala.annotation.tailrec

import net.fwbrasil.activate.storage.marshalling.StorageValue

trait JdbcStatement extends Serializable {
    val statement: String
    val restrictionQuery: Option[(String, Int)]
    val bindsList: List[Map[String, StorageValue]]
    def expectedNumbersOfAffectedRowsOption: List[Option[Int]]

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
    val binds: Map[String, StorageValue] = Map(),
    val restrictionQuery: Option[(String, Int)] = None,
    val expectedNumberOfAffectedRowsOption: Option[Int] = None)
        extends JdbcStatement {

    override def expectedNumbersOfAffectedRowsOption =
        List(expectedNumberOfAffectedRowsOption)

    val bindsList = List(binds)

    def isCompatible(other: SqlStatement) =
        statement == other.statement &&
            restrictionQuery == other.restrictionQuery

}

class BatchSqlStatement(
    val statement: String,
    val bindsList: List[Map[String, StorageValue]],
    val restrictionQuery: Option[(String, Int)],
    override val expectedNumbersOfAffectedRowsOption: List[Option[Int]])
        extends JdbcStatement

object BatchSqlStatement {

    @tailrec def group(sqlStatements: List[SqlStatement], batchLimit: Int, grouped: List[JdbcStatement] = List()): List[JdbcStatement] = {
        if (batchLimit <= 1)
            sqlStatements
        else if (sqlStatements.isEmpty)
            grouped
        else {
            val (head :: tail) = sqlStatements
            var batchSize = 0
            val (tailToGroup, others) = tail.span(each => {
                batchSize += 1
                each.isCompatible(head) &&
                    batchSize <= batchLimit
            })
            if (tailToGroup.isEmpty)
                group(others, batchLimit, grouped ++ List(head))
            else {
                val toGroup = List(head) ++ tailToGroup
                val expectedNumberOfAffectedRows = toGroup.map(_.expectedNumbersOfAffectedRowsOption).flatten
                val batch = new BatchSqlStatement(head.statement, toGroup.map(_.binds), head.restrictionQuery, expectedNumberOfAffectedRows)
                group(others, batchLimit, grouped ++ List(batch))
            }
        }
    }
}
