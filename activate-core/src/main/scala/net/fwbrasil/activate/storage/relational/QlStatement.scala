package net.fwbrasil.activate.storage.relational

import java.util.regex.Pattern

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

import net.fwbrasil.activate.storage.marshalling.StorageValue

trait QlStatement extends Serializable {
    val statement: String
    val restrictionQuery: Option[(String, Int)]
    val bindsList: List[Map[String, StorageValue]]
    def expectedNumbersOfAffectedRowsOption: List[Option[Int]]

    lazy val (indexedStatement, valuesList) = {
        val pattern = Pattern.compile("(:[a-zA-Z0-9_]*)")
        var matcher = pattern.matcher(statement)
        var result = statement
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

class NormalQlStatement(
    val statement: String, 
    val binds: Map[String, StorageValue] = Map(), 
    val restrictionQuery: Option[(String, Int)] = None, 
    val expectedNumberOfAffectedRowsOption: Option[Int] = None)
        extends QlStatement {

    override def expectedNumbersOfAffectedRowsOption =
        List(expectedNumberOfAffectedRowsOption)

    val bindsList = List(binds)

    def isCompatible(other: NormalQlStatement) =
        statement == other.statement &&
            restrictionQuery == other.restrictionQuery

}

class BatchQlStatement(
    val statement: String,
    val bindsList: List[Map[String, StorageValue]],
    val restrictionQuery: Option[(String, Int)],
    override val expectedNumbersOfAffectedRowsOption: List[Option[Int]])
        extends QlStatement

object BatchQlStatement {

    @tailrec def group(qlStatements: List[NormalQlStatement], batchLimit: Int, grouped: List[QlStatement] = List()): List[QlStatement] = {
        if (batchLimit <= 1)
            qlStatements
        else if (qlStatements.isEmpty)
            grouped
        else {
            val (head :: tail) = qlStatements
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
                val batch = new BatchQlStatement(head.statement, toGroup.map(_.binds), head.restrictionQuery, expectedNumberOfAffectedRows)
                group(others, batchLimit, grouped ++ List(batch))
            }
        }
    }
}
