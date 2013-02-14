package net.fwbrasil.activate

import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.util.IdentityHashMap._
import net.fwbrasil.activate.util.IdentityHashMap
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.EntityValidation
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.radon.transaction.NestedTransaction
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.coordinator.Coordinator
import net.fwbrasil.radon.ConcurrentTransactionException
import net.fwbrasil.radon.transaction.TransactionManager
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.storage.Storage
import scala.util.Try
import scala.util.Success
import scala.util.Failure

class ActivateConcurrentTransactionException(val entitiesIds: Set[String], refs: List[Ref[_]]) extends ConcurrentTransactionException(refs)

trait DurableContext {
    this: ActivateContext =>

    val contextId = UUIDUtil.generateUUID

    override protected[fwbrasil] val transactionManager =
        new TransactionManager()(this) {
            override protected def waitToRetry(e: ConcurrentTransactionException) = {
                e match {
                    case e: ActivateConcurrentTransactionException =>
                        reloadEntities(e.entitiesIds)
                    case other =>
                }
                super.waitToRetry(e)
            }
        }

    protected lazy val coordinatorClientOption =
        Coordinator.clientOption(this)

    protected def reinitializeCoordinator = {
        coordinatorClientOption.map { coordinatorClient =>
            coordinatorClient.reinitialize
        }
    }

    protected def startCoordinator =
        coordinatorClientOption.map(coordinatorClient => {
            if (storages.forall(!_.isMemoryStorage))
                throw new IllegalStateException("Memory storages doesn't support coordinator")
        })

    def reloadEntities(ids: Set[String]) = {
        liveCache.uninitialize(ids)
        coordinatorClientOption.get.removeNotifications(ids)
    }

    private def runWithCoordinatorIfDefined(reads: => Set[String], writes: => Set[String])(f: => Unit) =
        coordinatorClientOption.map { coordinatorClient =>

            import language.existentials

            val (readLocksNok, writeLocksNok) = coordinatorClient.tryToAcquireLocks(reads, writes)
            if (readLocksNok.nonEmpty || writeLocksNok.nonEmpty)
                throw new ActivateConcurrentTransactionException(readLocksNok ++ writeLocksNok, List())
            try
                f
            finally {
                val (readUnlocksNok, writeUnlocksNok) = coordinatorClient.releaseLocks(reads, writes)
                if (readUnlocksNok.nonEmpty || writeUnlocksNok.nonEmpty)
                    throw new IllegalStateException("Can't release locks.")
            }

        }.getOrElse(f)

    override def makeDurable(transaction: Transaction) = {
        lazy val statements = statementsForTransaction(transaction)

        val (inserts, updates, deletes) = filterVars(transaction.assignments)

        val entities = inserts.keys.toList ++ updates.keys.toList ++ deletes.keys.toList

        lazy val writes = entities.map(_.id).toSet
        lazy val reads = (transaction.reads.map(_.asInstanceOf[Var[_]].outerEntity)).map(_.id).toSet

        runWithCoordinatorIfDefined(reads, writes) {
            if (inserts.nonEmpty || updates.nonEmpty || deletes.nonEmpty || statements.nonEmpty) {
                validateTransactionEnd(transaction, entities)
                store(statements.toList, inserts, updates, deletes)
                setPersisted(inserts.keys)
                deleteFromLiveCache(deletes.keys)
                statements.clear
            }
        }
    }

    private def store(
        statements: List[MassModificationStatement],
        insertList: IdentityHashMap[Entity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        updateList: IdentityHashMap[Entity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        deleteList: IdentityHashMap[Entity, IdentityHashMap[Var[Any], EntityValue[Any]]]) = {

        val statementsByStorage =
            statements.groupBy(s => storageFor(s.from.entitySources.onlyOne.entityClass)).withDefault(s => List())
        val insertsByStorage =
            insertList.groupBy(tuple => storageFor(tuple._1.niceClass)).withDefault(s => Map())
        val updatesByStorage =
            updateList.groupBy(tuple => storageFor(tuple._1.niceClass)).withDefault(s => Map())
        val deletesByStorage =
            deleteList.groupBy(tuple => storageFor(tuple._1.niceClass)).withDefault(s => Map())
        val storages = (statementsByStorage.keys.toSet ++ insertsByStorage.keys.toSet ++
            updatesByStorage.keys.toSet ++ deletesByStorage.keys.toSet).toList.sortBy(s => if (s == storage) -1 else 0)
        verifyMassSatatements(storages, statementsByStorage)
        var exceptionOption: Option[Throwable] = None
        val storagesToRollback =
            storages.takeWhile { storage =>
                Try(storage.toStorage(
                    statementsByStorage(storage),
                    insertsByStorage(storage).mapValues(_.map(tuple => (tuple._1.name, tuple._2)).toMap).toList,
                    updatesByStorage(storage).mapValues(_.map(tuple => (tuple._1.name, tuple._2)).toMap).toList,
                    deletesByStorage(storage).mapValues(_.map(tuple => (tuple._1.name, tuple._2)).toMap).toList)) match {
                    case Success(unit) =>
                        true
                    case Failure(exception) =>
                        exceptionOption = Some(exception)
                        false
                }
            }
        exceptionOption.map { exception =>
            storagesToRollback.foreach { storage =>
                val deletes = insertsByStorage(storage)
                val updates = updatesByStorage(storage).map(tuple => (tuple._1, tuple._2.map(tuple => (tuple._1, tuple._1.tval(tuple._1.snapshotWithoutTransaction)))))
                val inserts = deletesByStorage(storage)
                storage.toStorage(
                    List(),
                    inserts.mapValues(_.map(tuple => (tuple._1.name, tuple._2)).toMap).toList,
                    updates.mapValues(_.map(tuple => (tuple._1.name, tuple._2)).toMap).toList,
                    deletes.mapValues(_.map(tuple => (tuple._1.name, tuple._2)).toMap).toList)
            }
            throw exception
        }
    }

    private[this] def setPersisted(entities: Iterable[Entity]) =
        entities.foreach(_.setPersisted)

    private[this] def deleteFromLiveCache(entities: Iterable[Entity]) =
        entities.foreach(liveCache.delete)

    private def filterVars(pAssignments: List[(Ref[Any], Option[Any], Boolean)]) = {
        def normalize(assignments: List[(Var[Any], Option[Any], Boolean)]) = {
            val map = new IdentityHashMap[Entity, IdentityHashMap[Var[Any], EntityValue[Any]]]
            for ((ref, valueOption, isTransient) <- assignments)
                map.getOrElseUpdate(ref.outerEntity, new IdentityHashMap).put(ref, ref.tval(valueOption))
            map
        }
        // Assume that all assignments are of Vars for performance reasons (could be Ref)
        val persistentAssignments = pAssignments.asInstanceOf[List[(Var[Any], Option[Any], Boolean)]].filterNot(_._1.isTransient)
        val (deleteAssignments, modifyAssignments) = persistentAssignments.partition(_._3)
        val deleteAssignmentsNormalized = normalize(deleteAssignments)
        val (insertStatementsNormalized, updateStatementsNormalized) = normalize(modifyAssignments).partition(tuple => !tuple._1.isPersisted)
        (insertStatementsNormalized, updateStatementsNormalized, deleteAssignmentsNormalized)
    }

    private def validateTransactionEnd(transaction: Transaction, entities: List[Entity]) = {
        val toValidate = entities.filter(EntityValidation.validatesOnTransactionEnd(_, transaction))
        if (toValidate.nonEmpty) {
            val nestedTransaction = new NestedTransaction(transaction)
            try transactional(nestedTransaction) {
                // Missing a toSet here! Be careful with entity hash
                toValidate.foreach(_.validate)
            } finally
                nestedTransaction.rollback
        }
    }

    private def verifyMassSatatements(
        storages: List[Storage[_]],
        statementsByStorage: Map[Storage[Any], List[MassModificationStatement]]) =
        if (statementsByStorage.size != 0)
            if (statementsByStorage.size > 1)
                throw new UnsupportedOperationException("It is not possible to have mass statements from different storages in the same transaction.")
            else {
                val statementStorage = statementsByStorage.keys.onlyOne
                if ((storages.toSet - statementStorage).nonEmpty)
                    throw new UnsupportedOperationException("If there is a mass statement, all entities modifications must be to the same storage.")
            }

}