package net.fwbrasil.activate.storage.relational.async

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration

import com.twitter.finagle.exp.mysql.Client
import com.twitter.finagle.exp.mysql.OK
import com.twitter.finagle.exp.mysql.ResultSet
import com.twitter.util.{Future => TwitterFuture}

import FutureBridge.scalaToTwitter
import FutureBridge.twitterToScala
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.relational.BatchQlStatement
import net.fwbrasil.activate.storage.relational.DdlStorageStatement
import net.fwbrasil.activate.storage.relational.NormalQlStatement
import net.fwbrasil.activate.storage.relational.QlStatement
import net.fwbrasil.activate.storage.relational.QueryStorageStatement
import net.fwbrasil.activate.storage.relational.RelationalStorage
import net.fwbrasil.activate.storage.relational.StorageStatement
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import net.fwbrasil.radon.transaction.TransactionalExecutionContext

trait FinagleMySQLStorage extends RelationalStorage[Client] {

    val client: Client
    val queryLimit = 1000
    val dialect: mySqlDialect = mySqlDialect
    val defaultTimeout: Duration = Duration.Inf
    lazy val executionContext = scala.concurrent.ExecutionContext.Implicits.global

    override def directAccess = client
    override def isMemoryStorage = false
    override def isSchemaless = false
    override def isTransactional = false
    override def supportsQueryJoin = true

    override protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[BaseEntity]]) = {
        val jdbcQuery = dialect.toSqlDml(QueryStorageStatement(query, entitiesReadFromCache))
        val result = queryAsync(jdbcQuery, expectedTypes)
        await(result)
    }

    override protected[activate] def queryAsync(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[BaseEntity]])(
            implicit context: TransactionalExecutionContext) =
        Future(dialect.toSqlDml(QueryStorageStatement(query, entitiesReadFromCache))).flatMap {
            jdbcStatement => queryAsync(jdbcStatement, expectedTypes)
        }(context.ctx.ectx)

    override protected[activate] def executeStatements(
        reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]],
        statements: List[StorageStatement]) = {
        await(executeStatementsAsync(reads, statements)(executionContext))
        None
    }

    override protected[activate] def executeStatementsAsync(
        reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]],
        sqls: List[StorageStatement])(implicit context: ExecutionContext): Future[Unit] = {
        val isDdl = sqls.find(_.isInstanceOf[DdlStorageStatement]).isDefined
        val sqlStatements =
            sqls.map(dialect.toSqlStatement).flatten
        verifyReads(reads).flatMap { _ =>
            sqlStatements.foldLeft(Future[Unit]())((future, statement) =>
                future.flatMap(_ => execute(statement, isDdl))(executionContext))
        }
    }

    private def execute(jdbcStatement: QlStatement, isDdl: Boolean) = {
        implicit val ectx = executionContext
        satisfyRestriction(jdbcStatement).flatMap { satisfy =>
            if (satisfy)
                jdbcStatement match {
                    case normal: NormalQlStatement =>
                        if (isDdl)
                            client.query(jdbcStatement.statement).map(_ => {})
                        else
                            queryAsync(jdbcStatement, List()).map(verifyStaleData(normal, _))
                    case batch: BatchQlStatement =>
                        throw new UnsupportedOperationException()
                }
            else
                TwitterFuture()
        }
    }

    private def satisfyRestriction(jdbcStatement: QlStatement) =
        jdbcStatement.restrictionQuery.map {
            case (query, expected) =>
                queryAsync(List(LongStorageValue(None)), List(), query).map {
                    _.head.head.asInstanceOf[LongStorageValue].value
                }.map {
                    _ == Option(expected.longValue)
                }
        }.getOrElse(TwitterFuture(true))

    private def verifyStaleData(jdbcStatement: NormalQlStatement, result: List[List[StorageValue]]): Unit =
        jdbcStatement.expectedNumberOfAffectedRowsOption.map { expected =>
            val success =
                result match {
                    case List(List(LongStorageValue(Some(value)))) =>
                        value.intValue == expected
                    case other =>
                        false
                }
            if (!success) {
                val id =
                    jdbcStatement.binds.get("id").map {
                        case StringStorageValue(Some(value: String)) =>
                            (value, jdbcStatement.entityClass)
                        case ReferenceStorageValue(value) =>
                            (value.value.get, jdbcStatement.entityClass)
                    }
                staleDataException(id.toSet.asInstanceOf[Set[(BaseEntity#ID, Class[BaseEntity])]])
            }
        }

    private def verifyReads(reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]])(implicit context: ExecutionContext) = {
        dialect.versionVerifyQueries(reads, queryLimit).foldLeft(Future[Unit]())((future, tuple) => {
            future.flatMap(_ => {
                val (stmt, referenceStorageValue, clazz) = tuple
                queryAsync(stmt, List(referenceStorageValue)).map {
                    _.map {
                        _ match {
                            case List(ReferenceStorageValue(storageValue)) =>
                                (storageValue.value.get.asInstanceOf[BaseEntity#ID], clazz)
                            case other =>
                                throw new IllegalStateException("Invalid version information")
                        }
                    }
                }.map {
                    inconsistentVersions =>
                        if (inconsistentVersions.nonEmpty)
                            staleDataException(inconsistentVersions.toSet)
                }
            })
        })
    }

    private def queryAsync(query: QlStatement, expectedTypes: List[StorageValue]): Future[List[List[StorageValue]]] = {
        implicit val ctx = executionContext
        val params = query.valuesList.head.map(dialect.toValue).collect {
            case value: BigDecimal =>
                value.floatValue
            case other =>
                other
        }
        val statement = query.indexedStatement
        queryAsync(expectedTypes, params, statement)
    }

    private def queryAsync(expectedTypes: List[StorageValue], params: List[Any], statement: String): TwitterFuture[List[List[StorageValue]]] =
        client.prepare(statement)(params: _*).flatMap {
            case ResultSet(fields, rows) =>
                val futures =
                    rows.toList.map { row =>
                        val resultSet = new FinagleResultSet(row.values.toList)
                        val futures = for ((expectedType, i) <- expectedTypes.zipWithIndex) yield {
                            getValue(resultSet, i, expectedType)
                        }
                        TwitterFuture.collect(futures).map(_.toList)
                    }
                TwitterFuture.collect(futures).map(_.toList)
            case ok: OK =>
                TwitterFuture(List(List(LongStorageValue(Some(ok.affectedRows)))))
            case other =>
                throw new IllegalStateException(s"Invalid response: $other")
        }

    private def getValue(rs: FinagleResultSet, i: Int, expectedType: StorageValue): TwitterFuture[StorageValue] =
        expectedType match {
            case value: ListStorageValue =>
                loadList(rs, i, value)
            case other =>
                TwitterFuture(dialect.getValue(rs, i, other))
        }

    private def loadList(rs: FinagleResultSet, i: Int, expectedType: ListStorageValue) = {
        val split = rs.getString(i).getOrElse("0").split('|')
        val notEmptyFlag = split.head
        if (notEmptyFlag != "1")
            TwitterFuture(ListStorageValue(None, expectedType.emptyStorageValue))
        else {
            val sql = split.tail.head
            queryAsync(List(expectedType.emptyStorageValue), List(), sql).map { value =>
                ListStorageValue(Some(value.map(_.head)), expectedType.emptyStorageValue)
            }
        }
    }

    private def await[R](future: Future[R]) =
        Await.result(future, defaultTimeout)
}
