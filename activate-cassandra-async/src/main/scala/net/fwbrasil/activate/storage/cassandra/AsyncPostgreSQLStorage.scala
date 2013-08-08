package net.fwbrasil.activate.storage.cassandra

import java.sql.Timestamp

import scala.Option.option2Iterable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.jboss.netty.util.CharsetUtil
import org.joda.time.DateTime

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.relational.BatchQlStatement
import net.fwbrasil.activate.storage.relational.DdlStorageStatement
import net.fwbrasil.activate.storage.relational.QlStatement
import net.fwbrasil.activate.storage.relational.QueryStorageStatement
import net.fwbrasil.activate.storage.relational.RelationalStorage
import net.fwbrasil.activate.storage.relational.NormalQlStatement
import net.fwbrasil.activate.storage.relational.StorageStatement
import net.fwbrasil.radon.transaction.TransactionalExecutionContext

class AsyncCassandraStorage extends RelationalStorage[None.type] {

    val defaultTimeout = 9999 seconds
    val executionContext = scala.concurrent.ExecutionContext.Implicits.global
    
    override def directAccess = None
    
    override def isMemoryStorage = false
    override def isSchemaless = true
    override def isTransactional = true
    override def supportsQueryJoin = false
    override def supportsAsync = true

    override protected[activate] def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[Entity]]) = {
        ???
    }
    
    override protected[activate] def queryAsync(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]])(implicit context: TransactionalExecutionContext): Future[List[List[StorageValue]]] = {
        ???
    }
    
    override protected[activate] def executeStatementsAsync(
        sqls: List[StorageStatement])(implicit context: ExecutionContext): Future[Unit] = {
        ???
    }
    
    override protected[activate] def executeStatements(
        sqls: List[StorageStatement]) = {
        ???
    }

}