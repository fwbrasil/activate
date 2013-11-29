//package net.fwbrasil.activate.storage.cassandra
//
//import scala.collection.JavaConversions._
//import java.sql.Timestamp
//import scala.Option.option2Iterable
//import scala.collection.mutable.ListBuffer
//import scala.concurrent.Await
//import scala.concurrent.ExecutionContext
//import scala.concurrent.Future
//import scala.concurrent.duration.DurationInt
//import org.jboss.netty.util.CharsetUtil
//import org.joda.time.DateTime
//import net.fwbrasil.activate.ActivateContext
//import net.fwbrasil.activate.entity.BaseEntity
//import net.fwbrasil.activate.statement.query.Query
//import net.fwbrasil.activate.storage.Storage
//import net.fwbrasil.activate.storage.StorageFactory
//import net.fwbrasil.activate.storage.TransactionHandle
//import net.fwbrasil.activate.storage.marshalling.ListStorageValue
//import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
//import net.fwbrasil.activate.storage.marshalling.StorageValue
//import net.fwbrasil.activate.storage.marshalling.StringStorageValue
//import net.fwbrasil.activate.storage.relational.BatchQlStatement
//import net.fwbrasil.activate.storage.relational.DdlStorageStatement
//import net.fwbrasil.activate.storage.relational.QlStatement
//import net.fwbrasil.activate.storage.relational.QueryStorageStatement
//import net.fwbrasil.activate.storage.relational.RelationalStorage
//import net.fwbrasil.activate.storage.relational.NormalQlStatement
//import net.fwbrasil.activate.storage.relational.StorageStatement
//import net.fwbrasil.radon.transaction.TransactionalExecutionContext
//import com.datastax.driver.core.Session
//import com.datastax.driver.core.Cluster
//import com.datastax.driver.core.BoundStatement
//import com.datastax.driver.core.Row
//import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
//import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
//import net.fwbrasil.activate.storage.marshalling.IntStorageValue
//import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
//import net.fwbrasil.activate.storage.marshalling.LongStorageValue
//import net.fwbrasil.activate.storage.marshalling.DateStorageValue
//import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
//import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
//import com.google.common.util.concurrent.Futures
//import com.google.common.util.concurrent.FutureCallback
//import com.google.common.util.concurrent.AbstractFuture
//import scala.concurrent.Promise
//import com.datastax.driver.core.ResultSet
//import net.fwbrasil.activate.storage.relational.DdlStorageStatement
//import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
//import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
//import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
//import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
//import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
//import net.fwbrasil.activate.storage.marshalling.StorageAddReference
//import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
//import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
//import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
//import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
//import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
//import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex
//import net.fwbrasil.activate.storage.marshalling.StorageModifyColumnType
//import net.fwbrasil.activate.storage.marshalling.ListStorageValue
//import java.nio.ByteBuffer
//import java.util.Date
//
//trait AsyncCassandraStorage extends RelationalStorage[Session] {
//
//    def contactPoints: List[String]
//    def keyspace: String
//
//    val cluster =
//        Cluster.builder.addContactPoints(contactPoints: _*).build
//
//    val dialect = cqlIdiom
//
//    private val session = cluster.connect
//
//    session.execute(s"USE $keyspace")
//
//    private def metadata = cluster.getMetadata.getKeyspace(keyspace.toLowerCase)
//
//    override def directAccess = session
//
//    override def isMemoryStorage = false
//    override def isSchemaless = true
//    override def isTransactional = true
//    override def supportsQueryJoin = false
//    override def supportsAsync = true
//
//    override protected[activate] def query(
//        query: Query[_],
//        expectedTypes: List[StorageValue],
//        entitiesReadFromCache: List[List[BaseEntity]]) = {
//        val boundStatement = toBoundStatement(query, entitiesReadFromCache)
//        val resultSet = session.execute(boundStatement)
//        resultSetToValues(resultSet, expectedTypes)
//    }
//
//    override def queryAsync(
//        query: Query[_],
//        expectedTypes: List[StorageValue],
//        entitiesReadFromCache: List[List[BaseEntity]])(
//            implicit context: TransactionalExecutionContext) = {
//        val boundStatement = toBoundStatement(query, entitiesReadFromCache)
//        toScalaFuture(session.executeAsync(boundStatement))
//            .map(resultSetToValues(_, expectedTypes))
//    }
//
//    override protected[activate] def executeStatements(
//        reads: Map[Class[BaseEntity], List[(String, Long)]], 
//        storageStatements: List[StorageStatement]) = {
//        val statements = storageStatements.map(s => dialect.toSqlStatement(s).map((_, s))).flatten
//        for ((statement, storageStatement) <- statements) {
//            if (satisfyRestriction(storageStatement))
//                session.execute(toBoundStatement(statement))
//        }
//        None
//    }
//
//    override protected[activate] def executeStatementsAsync(
//        reads: Map[Class[BaseEntity], List[(String, Long)]], 
//        storageStatements: List[StorageStatement])(implicit context: ExecutionContext) = {
//        val statements = storageStatements.map(s => dialect.toSqlStatement(s).map((_, s))).flatten
//        statements.foldLeft(Future[Unit]())((future, tuple) => {
//            future.flatMap { _ =>
//                val (statement, storageStatement) = tuple
//                if (satisfyRestriction(storageStatement))
//                    toScalaFuture(session.executeAsync(toBoundStatement(statement))).map { _ => }
//                else
//                    Future.successful()
//            }
//        })
//    }
//
//    private def satisfyRestriction(storageStatement: StorageStatement): Boolean = {
//        storageStatement match {
//            case storageStatement: DdlStorageStatement =>
//                satisfyRestrictionDdl(storageStatement)
//            case other =>
//                true
//        }
//    }
//
//    private def getTable(name: String) =
//        Option(metadata.getTable(name))
//
//    private def getColumn(table: String, column: String) =
//        getTable(table).flatMap(t => Option(t.getColumn(column)))
//
//    private def satisfyRestrictionDdl(storageStatement: DdlStorageStatement) = {
//        storageStatement.action match {
//            case action: StorageCreateListTable =>
//                !action.ifNotExists || getTable(action.listTableName).isEmpty
//            case action: StorageRemoveListTable =>
//                !action.ifExists || getTable(action.listTableName).nonEmpty
//            case action: StorageCreateTable =>
//                !action.ifNotExists || getTable(action.tableName).isEmpty
//            case action: StorageRenameTable =>
//                !action.ifExists || getTable(action.oldName).nonEmpty
//            case action: StorageRemoveTable =>
//                !action.ifExists || getTable(action.name).nonEmpty
//            case action: StorageAddColumn =>
//                !action.ifNotExists ||
//                    getColumn(action.tableName, action.column.name).isEmpty
//            case action: StorageRenameColumn =>
//                !action.ifExists ||
//                    getColumn(action.tableName, action.oldName).nonEmpty
//            case action: StorageModifyColumnType =>
//                !action.ifExists ||
//                    getColumn(action.tableName, action.column.name).nonEmpty
//            case action: StorageRemoveColumn =>
//                !action.ifExists ||
//                    getColumn(action.tableName, action.name).nonEmpty
//            case action: StorageAddIndex =>
//                !action.ifNotExists ||
//                    getColumn(action.tableName, action.columnName)
//                    .flatMap(v => Option(v.getIndex))
//                    .isEmpty
//            case action: StorageRemoveIndex =>
//                !action.ifExists ||
//                    getColumn(action.tableName, action.columnName)
//                    .flatMap(v => Option(v.getIndex))
//                    .nonEmpty
//            case action: StorageAddReference =>
//                true
//            case action: StorageRemoveReference =>
//                true
//        }
//    }
//
//    private def resultSetToValues(resultSet: ResultSet, expectedTypes: List[StorageValue]) = {
//        val rows = resultSet.all.iterator
//        (for (row <- rows) yield {
//            (for (i <- 0 until expectedTypes.length) yield {
//                getValue(row, i, expectedTypes(i))
//            }).toList
//        }).toList
//    }
//
//    private def toScalaFuture[T](future: AbstractFuture[T]) = {
//        val promise = Promise[T]()
//        Futures.addCallback(future,
//            new FutureCallback[T] {
//                def onSuccess(result: T) = promise.success(result)
//                def onFailure(t: Throwable) = promise.failure(t)
//            })
//        promise.future
//    }
//
//    private def getValue(row: Row, i: Int, storageValue: StorageValue): StorageValue = {
//        storageValue match {
//            case value: IntStorageValue =>
//                IntStorageValue(Option(row.getInt(i)))
//            case value: LongStorageValue =>
//                LongStorageValue(Option(row.getLong(i)))
//            case value: BooleanStorageValue =>
//                BooleanStorageValue(Option(row.getBool(i)))
//            case value: StringStorageValue =>
//                StringStorageValue(Option(row.getString(i)))
//            case value: FloatStorageValue =>
//                FloatStorageValue(Option(row.getFloat(i)))
//            case value: DateStorageValue =>
//                DateStorageValue(Option(row.getDate(i)))
//            case value: DoubleStorageValue =>
//                DoubleStorageValue(Option(row.getDouble(i)))
//            case value: BigDecimalStorageValue =>
//                BigDecimalStorageValue(Option(row.getDecimal(i)).map(BigDecimal(_)))
//            case value: ByteArrayStorageValue =>
//                ByteArrayStorageValue(Option(row.getBytes(i)).map(toArray))
//            case value: ReferenceStorageValue =>
//                ReferenceStorageValue(Option(row.getString(i)))
//            case value: ListStorageValue =>
//                val list = row.getList(i, classOf[Any])
//                val mapped = Option(list).map(_.map(fromValue(_, value.emptyStorageValue)).toList)
//                ListStorageValue(mapped, value.emptyStorageValue)
//        }
//    }
//
//    private def fromValue(value: Any, storageValue: StorageValue) =
//        (value, storageValue) match {
//            case (value: Int, storageValue: IntStorageValue) =>
//                IntStorageValue(Option(value))
//            case (value: Long, storageValue: LongStorageValue) =>
//                LongStorageValue(Option(value))
//            case (value: Boolean, storageValue: BooleanStorageValue) =>
//                BooleanStorageValue(Option(value))
//            case (value: String, storageValue: StringStorageValue) =>
//                StringStorageValue(Option(value))
//            case (value: Float, storageValue: FloatStorageValue) =>
//                FloatStorageValue(Option(value))
//            case (value: Date, storageValue: DateStorageValue) =>
//                DateStorageValue(Option(value))
//            case (value: Double, storageValue: DoubleStorageValue) =>
//                DoubleStorageValue(Option(value))
//            case (value: java.math.BigDecimal, storageValue: BigDecimalStorageValue) =>
//                BigDecimalStorageValue(Option(value))
//            case (value: ByteBuffer, storageValue: ByteArrayStorageValue) =>
//                ByteArrayStorageValue(Option(value).map(toArray))
//            case (value: String, storageValue: ReferenceStorageValue) =>
//                ReferenceStorageValue(Option(value))
//        }
//
//    private def toValue(storageValue: StorageValue): Any =
//        storageValue match {
//            case value: ListStorageValue =>
//                value.value.map(_.map(toValue): java.util.List[Any]).getOrElse(null)
//            case value: BigDecimalStorageValue =>
//                value.value.map(_.bigDecimal).getOrElse(null)
//            case value: ByteArrayStorageValue =>
//                value.value.map(ByteBuffer.wrap).getOrElse(null)
//            case other =>
//                other.value.getOrElse(null)
//        }
//
//    private def toArray(buffer: ByteBuffer) = {
//        val array = new Array[Byte](buffer.remaining)
//        buffer.get(array)
//        array
//    }
//
//    private def toBoundStatement(
//        query: Query[_],
//        entitiesReadFromCache: List[List[BaseEntity]]): BoundStatement =
//        toBoundStatement(dialect.toSqlDml(QueryStorageStatement(query, entitiesReadFromCache)))
//
//    private def toBoundStatement(statement: NormalQlStatement): BoundStatement = {
//        val preparedStatement = session.prepare(statement.indexedStatement)
//        new BoundStatement(preparedStatement)
//            .bind(statement.valuesList.head.map(toValue)
//                .toSeq.asInstanceOf[Seq[Object]]: _*)
//    }
//
//}