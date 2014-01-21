package net.fwbrasil.activate.storage.relational.async

import scala.Option.option2Iterable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.postfixOps
import com.github.mauricio.async.db.Connection
import com.github.mauricio.async.db.pool.ConnectionPool
import com.github.mauricio.async.db.pool.ObjectFactory
import com.github.mauricio.async.db.pool.PoolConfiguration
import io.netty.util.CharsetUtil
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageOptionalValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.relational.BatchQlStatement
import net.fwbrasil.activate.storage.relational.DdlStorageStatement
import net.fwbrasil.activate.storage.relational.NormalQlStatement
import net.fwbrasil.activate.storage.relational.QlStatement
import net.fwbrasil.activate.storage.relational.QueryStorageStatement
import net.fwbrasil.activate.storage.relational.RelationalStorage
import net.fwbrasil.activate.storage.relational.StorageStatement
import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import net.fwbrasil.activate.OptimisticOfflineLocking

trait AsyncSQLStorage[C <: Connection] extends RelationalStorage[Future[C]] {

    val defaultTimeout: Duration = Duration.Inf
    lazy val executionContext = scala.concurrent.ExecutionContext.Implicits.global

    val objectFactory: ObjectFactory[C]
    def charset = CharsetUtil.UTF_8
    def poolConfiguration = PoolConfiguration.Default

    private lazy val pool = new ConnectionPool(objectFactory, poolConfiguration, executionContext = executionContext)

    val queryLimit = 1000
    val dialect: SqlIdiom

    override protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[BaseEntity]]) = {
        val jdbcQuery = dialect.toSqlDml(QueryStorageStatement(query, entitiesReadFromCache))
        val result = queryAsync(jdbcQuery, expectedTypes)
        Await.result(result, defaultTimeout)
    }

    override protected[activate] def queryAsync(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[BaseEntity]])(implicit context: TransactionalExecutionContext): Future[List[List[StorageValue]]] = {
        Future(dialect.toSqlDml(QueryStorageStatement(query, entitiesReadFromCache))).flatMap {
            jdbcStatement => queryAsync(jdbcStatement, expectedTypes)
        }(context.ctx.ectx)
    }

    private def queryAsync(query: NormalQlStatement, expectedTypes: List[StorageValue]): Future[List[List[StorageValue]]] = {
        implicit val ctx = executionContext
        val resultSetFuture = sendPreparedStatement(query, pool)
        resultSetFuture.map(
            _.rows match {
                case Some(resultSet) =>
                    resultSet.map {
                        row =>
                            val rs = JdbcRelationalAsyncResultSet(row, charset.name)
                            var i = 0
                            val list = ListBuffer[StorageValue]()
                            for (storageValue <- expectedTypes) {
                                list += getValue(rs, i, storageValue)
                                i += 1
                            }
                            list.toList
                    }.toList
                case None =>
                    throw new IllegalStateException("Empty result.")
            })
    }

    private def getValue(rs: JdbcRelationalAsyncResultSet, i: Int, expectedType: StorageValue)(implicit context: ExecutionContext): StorageValue = {
        try expectedType match {
            case value: ListStorageValue =>
                loadList(rs, i, value)
            case other =>
                dialect.getValue(rs, i, other)
        } catch {
            case e: ArrayIndexOutOfBoundsException =>
                throw e
        }
    }

    private def loadList(rs: JdbcRelationalAsyncResultSet, i: Int, expectedType: ListStorageValue)(implicit context: ExecutionContext) = {
        // TODO review. It should be async too!
        val split = rs.getString(i).getOrElse("0").split('|')
        val notEmptyFlag = split.head
        val listOption =
            if (notEmptyFlag != "1")
                None
            else {
                val sql = split.tail.head
                val listFuture =
                    pool.sendQuery(sql).map {
                        _.rows match {
                            case Some(resultSet) =>
                                resultSet.map {
                                    row =>
                                        val rs = JdbcRelationalAsyncResultSet(row, charset.name)
                                        getValue(rs, 0, expectedType.emptyStorageValue)
                                }.toList
                            case None =>
                                throw new IllegalStateException("Empty result.")
                        }
                    }
                Some(Await.result(listFuture, defaultTimeout))
            }
        ListStorageValue(listOption, expectedType.emptyStorageValue)
    }

    override protected[activate] def executeStatementsAsync(
        reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]],
        sqls: List[StorageStatement])(implicit context: ExecutionContext): Future[Unit] = {
        val isDdl = sqls.find(_.isInstanceOf[DdlStorageStatement]).isDefined
        val sqlStatements =
            sqls.map(dialect.toSqlStatement).flatten
        pool.inTransaction { connection =>
            verifyReads(reads).flatMap { _ =>
                sqlStatements.foldLeft(Future[Unit]())((future, statement) =>
                    future.flatMap(_ => execute(statement, connection, isDdl))(executionContext))
            }
        }
    }

    override protected[activate] def executeStatements(
        reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]],
        statements: List[StorageStatement]) = {
        implicit val ectx = executionContext
        val isDdl = statements.find(_.isInstanceOf[DdlStorageStatement]).isDefined
        val sqlStatements =
            statements.map(dialect.toSqlStatement).flatten
        val res =
            executeWithTransactionAndReturnHandle {
                connection =>
                    verifyReads(reads).flatMap { _ =>
                        sqlStatements.foldLeft(Future[Unit]())((future, statement) =>
                            future.flatMap(_ => execute(statement, connection, isDdl)))
                    }
            }
        Some(Await.result(res, defaultTimeout))
    }

    private def verifyReads(reads: Map[Class[BaseEntity], List[(ReferenceStorageValue, Long)]])(implicit context: ExecutionContext) = {
        dialect.versionVerifyQueries(reads, queryLimit).foldLeft(Future[Unit]())((future, tuple) => {
            future.flatMap(_ => {
                val (stmt, referenceStorageValue, clazz) = tuple
                queryAsync(stmt, List(new StringStorageValue(None))).map {
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

    def execute(jdbcStatement: QlStatement, connection: Connection, isDdl: Boolean) = {
        implicit val ectx = executionContext
        satisfyRestriction(jdbcStatement).flatMap { satisfy =>
            if (satisfy)
                jdbcStatement match {
                    case normal: NormalQlStatement =>
                        val future =
                            if (isDdl)
                                connection.sendQuery(jdbcStatement.statement)
                            else
                                sendPreparedStatement(jdbcStatement, connection)
                        future.map {
                            queryResult =>
                                if (OptimisticOfflineLocking.isEnabled)
                                    verifyStaleData(
                                        jdbcStatement,
                                        Array(queryResult.rowsAffected))
                        }
                    case batch: BatchQlStatement =>
                        throw new UnsupportedOperationException()
                }
            else
                Future.successful()
        }
    }

    private def verifyStaleData(jdbcStatement: QlStatement, result: Array[Long]): Unit = {
        val expectedResult = jdbcStatement.expectedNumbersOfAffectedRowsOption
        require(result.size == expectedResult.size)
        val invalidIds =
            (for (i <- 0 until result.size) yield {
                expectedResult(i).filter(_ != result(i).intValue).map(_ => i)
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

    private protected[activate] def satisfyRestriction(jdbcStatement: QlStatement)(implicit context: ExecutionContext) =
        jdbcStatement.restrictionQuery.map(tuple => {
            val (query, expected) = tuple
            pool.sendQuery(query).map {
                _.rows match {
                    case Some(resultSet) =>
                        resultSet(0)(0).asInstanceOf[Long]
                    case None =>
                        throw new IllegalStateException("Empty result")
                }
            }.map {
                _ == expected
            }
        }).getOrElse(Future(true))

    def executeWithTransactionAndReturnHandle(f: (Connection) => Future[Unit]) = {
        implicit val ectx = executionContext
        pool.take.flatMap {
            connection =>
                connection.inTransaction { _ =>
                    val res =
                        f(connection).map { _ =>
                            new TransactionHandle(
                                commitBlock = () => commit(connection),
                                rollbackBlock = () => rollback(connection),
                                finallyBlock = () => pool.giveBack(connection))
                        }
                    res.onFailure {
                        case e: Throwable =>
                            pool.giveBack(connection)
                            throw e
                    }
                    res
                }
        }
    }

    private def commit(c: Connection) =
        Await.result(c.sendQuery("COMMIT"), defaultTimeout)

    private def rollback(c: Connection) =
        Await.result(c.sendQuery("ROLLBACK"), defaultTimeout)

    def directAccess = pool.take

    def isMemoryStorage = false
    def isSchemaless = false
    def isTransactional = true
    def supportsQueryJoin = true
    override def supportsAsync = true

    private def sendPreparedStatement(jdbcStatement: QlStatement, connection: Connection) =
        connection.sendPreparedStatement(
            jdbcStatement.indexedStatement,
            jdbcStatement.valuesList.head.map(dialect.toValue))
}