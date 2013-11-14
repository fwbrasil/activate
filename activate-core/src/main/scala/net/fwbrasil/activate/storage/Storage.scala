package net.fwbrasil.activate.storage

import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.ActivateProperties
import net.fwbrasil.activate.ActivateProperties
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import scala.annotation.implicitNotFound
import net.fwbrasil.activate.ActivateConcurrentTransactionException
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.radon.transaction.TransactionalExecutionContext

class TransactionHandle(
        private val commitBlock: () => Unit,
        private val rollbackBlock: () => Unit,
        private val finallyBlock: () => Unit) {
    def commit =
        try commitBlock()
        finally finallyBlock()
    def rollback =
        try rollbackBlock()
        finally finallyBlock()
}

trait Storage[T] extends Logging {

    protected[activate] def toStorage(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])]): Option[TransactionHandle]

    protected[activate] def toStorageAsync(
        readList: List[(Entity, Long)],    
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])])(implicit ecxt: ExecutionContext): Future[Unit] =
        blockingFuture(toStorage(readList, statements, insertList, updateList, deleteList).map(_.commit))

    protected[activate] def fromStorage(query: Query[_], entitiesReadFromCache: List[List[Entity]]): List[List[EntityValue[_]]]

    protected[activate] def fromStorageAsync(query: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit ecxt: TransactionalExecutionContext): Future[List[List[EntityValue[_]]]] =
        blockingFuture(fromStorage(query, entitiesReadFromCache))

    private var blockingFutureWarned = false

    protected def blockingFuture[T](f: => T)(implicit ctx: ExecutionContext) = {
        if (!blockingFutureWarned)
            warn("Storage does not support non-blocking async operations. Async operations will run inside blocking futures.")
        blockingFutureWarned = true
        Future(f)
    }

    def directAccess: T

    def isMemoryStorage: Boolean
    def isSchemaless: Boolean
    def isTransactional: Boolean
    def supportsQueryJoin: Boolean
    def supportsAsync = false
    def supportsLimitedQueries = true
    def supportsRegex = true

    protected[activate] def reinitialize = {}
    protected[activate] def migrate(action: StorageAction): Unit
    protected[activate] def prepareDatabase = {}
    protected def staleDataException(entityIds: Set[(Entity#ID, Class[Entity])]) =
        throw new ActivateConcurrentTransactionException(entityIds, List())

}

trait StorageFactory {
    def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_]
}

object StorageFactory {
    @implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
    def fromSystemProperties(name: String)(implicit context: ActivateContext) = {
        import scala.collection.JavaConversions._
        val properties =
            new ActivateProperties(Option(context.properties), "storage")
        val factoryClassName =
            properties.getProperty(name, "factory")
        val storageFactory =
            Reflection.getCompanionObject[StorageFactory](ActivateContext.loadClass(factoryClassName)).get
        storageFactory.buildStorage(properties.childProperties(name))
    }
}
