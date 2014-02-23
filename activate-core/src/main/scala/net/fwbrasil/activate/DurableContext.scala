package net.fwbrasil.activate

import scala.collection.mutable.{ Map => MutableMap, Set => MutableSet }
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
import net.fwbrasil.radon.ConcurrentTransactionException
import net.fwbrasil.radon.transaction.TransactionManager
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.storage.Storage
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.entity.LongEntityValue
import net.fwbrasil.activate.storage.TransactionHandle
import java.io.File
import java.io.FileOutputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.cache.CustomCache
import org.joda.time.DateTime
import scala.concurrent.duration.Duration
import scala.annotation.tailrec
import net.fwbrasil.activate.entity.BaseEntity

class ActivateConcurrentTransactionException(
    val entitiesIds: Set[(BaseEntity#ID, Class[BaseEntity])],
    refs: List[Ref[_]])
        extends ConcurrentTransactionException(refs)

trait DurableContext {
    this: ActivateContext =>

    val contextId = UUIDUtil.generateUUID

    override protected[fwbrasil] val transactionManager =
        new TransactionManager()(this) {
            override protected def waitToRetry(e: ConcurrentTransactionException) = {
                e match {
                    case e: ActivateConcurrentTransactionException =>
                        super.waitToRetry(e)
                        reloadEntities(e.entitiesIds)
                    case other =>
                        super.waitToRetry(e)
                }
            }
        }

    def reloadEntities(ids: Set[(BaseEntity#ID, Class[BaseEntity])]) = {
        val entities =
            liveCache.reloadEntities(ids)
                .toList.asInstanceOf[List[BaseEntity]]
        if (entities.nonEmpty)
            updateIndexes(inserts = List(), entities, deletes = List())
    }

    override def beforeCommit(transaction: Transaction): Unit =
        if (!transaction.transient)
            beforeCommit(transaction, List())

    private def beforeCommit(transaction: Transaction, seenEntities: List[BaseEntity#ID]): Unit = {
        import scala.collection.JavaConversions._
        def assignments =
            transaction.refsSnapshot.values
                .filter(snap => snap.isWrite && !snap.destroyedFlag)
                .map(_.ref.asInstanceOf[Var[Any]].outerEntity)
                .groupBy(System.identityHashCode)
                .mapValues(_.head)
                .values.toList
        val writes = assignments
        val entitiesMap = writes.groupBy(_.id).mapValues(_.head)
        val entities = entitiesMap.filterKeys(!seenEntities.contains(_)).values
        entities.foreach(_.beforeInsertOrUpdate)
        val (persistedEntities, newEntities) = entities.partition(_.isPersisted)
        persistedEntities.foreach(_.beforeUpdate)
        newEntities.foreach(_.beforeInsert)
        if (writes != assignments)
            beforeCommit(transaction, entities.map(_.id).toList)
    }

    override def makeDurable(transaction: Transaction): Unit = {
        val statements = statementsForTransaction(transaction)
        val assignments = transaction.assignments
        val entitiesRead = entitiesReadVersions(transaction)
        if (entitiesRead.nonEmpty || statements.nonEmpty || assignments.nonEmpty) {
            val (inserts, updates, deletesUnfiltered) = filterVars(assignments.toList)
            val deletes = filterDeletes(transaction, statements, deletesUnfiltered)
            val insertsEntities = inserts.keys.toList
            val updatesEntities = updates.keys.toList
            val deletesEntities = deletes.keys.toList
            val entities = insertsEntities ++ updatesEntities ++ deletesEntities
            validateTransactionEnd(transaction, entities)
            val entitiesReadOnly = entitiesRead -- entities
            store(statements.toList, inserts, updates, deletes, entitiesReadOnly)
            (entities ++ entitiesReadOnly.keys).foreach(_.lastVersionValidation = DateTime.now.getMillis)
            setPersisted(inserts.keys)
            deleteFromLiveCache(deletesUnfiltered.keys)
            updateCachedQueries(transaction, insertsEntities, updatesEntities, deletesEntities)
            transaction.attachments += (() => {
                if (customCaches.nonEmpty)
                    for (entity <- (insertsEntities ++ deletesEntities))
                        customCaches.foreach(
                            _.asInstanceOf[CustomCache[BaseEntity]].add(entity)(context))
                updateIndexes(insertsEntities, updatesEntities, deletesEntities)
            })
        }
    }

    override def afterCommit(transaction: Transaction): Unit = {
        transaction.attachments.collect {
            case f: Function0[_] =>
                f()
        }
    }

    override def makeDurableAsync(transaction: Transaction)(implicit ectx: ExecutionContext): Future[Unit] = {
        // TODO Refactoring (see makeDurable)
        val statements = statementsForTransaction(transaction)
        val assignments = transaction.assignments
        val entitiesRead = entitiesReadVersions(transaction)
        if (entitiesRead.nonEmpty || statements.nonEmpty || assignments.nonEmpty) {
            val (inserts, updates, deletesUnfiltered) = filterVars(transaction.assignments.toList)
            val deletes = filterDeletes(transaction, statements, deletesUnfiltered)
            val insertsEntities = inserts.keys.toList
            val updatesEntities = updates.keys.toList
            val deletesEntities = deletes.keys.toList
            val entities = insertsEntities ++ updatesEntities ++ deletesEntities
            Future(validateTransactionEnd(transaction, entities)).flatMap { _ =>
                val entitiesReadOnly = entitiesRead -- entities
                storeAsync(statements.toList, inserts, updates, deletes, entitiesReadOnly).map { _ =>
                    (entities ++ entitiesReadOnly.keys).foreach(_.lastVersionValidation = DateTime.now.getMillis)
                    setPersisted(inserts.keys)
                    deleteFromLiveCache(deletesUnfiltered.keys)
                    updateCachedQueries(transaction, insertsEntities, updatesEntities, deletesEntities)
                    transaction.attachments += (() => {
                        if (customCaches.nonEmpty)
                            for (entity <- (insertsEntities ++ deletesEntities))
                                customCaches.foreach(
                                    _.asInstanceOf[CustomCache[BaseEntity]].add(entity)(context))
                        updateIndexes(insertsEntities, updatesEntities, deletesEntities)
                    })
                }
            }
        } else
            Future.successful()
    }

    private def groupByStorage[T](iterable: Iterable[T])(f: T => Class[_ <: BaseEntity]) =
        iterable.groupBy(v => storageFor(f(v))).mapValues(_.toList).withDefault(v => List())

    private def mapVarsToName(list: List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]) =
        list.map(tuple => (tuple._1, tuple._2.map(tuple => (tuple._1.name, tuple._2)).toMap)).toList

    private def store(
        statements: List[MassModificationStatement],
        inserts: IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        updates: IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        deletes: IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        reads: IdentityHashMap[BaseEntity, Long]) = {

        val statementsByStorage = groupByStorage(statements)(_.from.entitySources.onlyOne.entityClass)
        val insertsByStorage = groupByStorage(inserts)(_._1.niceClass)
        val updatesByStorage = groupByStorage(updates)(_._1.niceClass)
        val deletesByStorage = groupByStorage(deletes)(_._1.niceClass)
        val readsByStorage = groupByStorage(reads)(_._1.niceClass)

        val storages = sortStorages((statementsByStorage.keys.toSet ++ insertsByStorage.keys.toSet ++
            updatesByStorage.keys.toSet ++ deletesByStorage.keys.toSet ++ readsByStorage.keys.toSet).toList)

        verifyMassSatatements(storages, statementsByStorage)
        twoPhaseCommit(
            readsByStorage,
            statementsByStorage,
            insertsByStorage,
            updatesByStorage,
            deletesByStorage,
            storages)

    }

    private def storeAsync(
        statements: List[MassModificationStatement],
        inserts: IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        updates: IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        deletes: IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]],
        reads: IdentityHashMap[BaseEntity, Long]): Future[Unit] = {

        // TODO Refactoring (see store)

        val statementsByStorage = groupByStorage(statements)(_.from.entitySources.onlyOne.entityClass)
        val insertsByStorage = groupByStorage(inserts)(_._1.niceClass)
        val updatesByStorage = groupByStorage(updates)(_._1.niceClass)
        val deletesByStorage = groupByStorage(deletes)(_._1.niceClass)
        val readsByStorage = groupByStorage(reads)(_._1.niceClass)

        val storages = sortStorages((statementsByStorage.keys.toSet ++ insertsByStorage.keys.toSet ++
            updatesByStorage.keys.toSet ++ deletesByStorage.keys.toSet).toList)

        verifyMassSatatements(storages, statementsByStorage)
        extendedCommit(readsByStorage, statementsByStorage, insertsByStorage, updatesByStorage, deletesByStorage, storages)
    }

    private def setPersisted(entities: Iterable[BaseEntity]) =
        entities.foreach(_.setPersisted)

    private def deleteFromLiveCache(entities: Iterable[BaseEntity]) =
        entities.foreach(liveCache.delete(_))

    private def filterVars(pAssignments: List[(Ref[Any], Option[Any], Boolean)]) = {
        def normalize(assignments: List[(Var[Any], Option[Any], Boolean)]) = {
            val map = new IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]]
            for ((ref, valueOption, isTransient) <- assignments)
                map.getOrElseUpdate(ref.outerEntity, new IdentityHashMap).put(ref, ref.tval(valueOption))
            map
        }
        // Assume that all assignments are of Vars for performance reasons (could be Refs)
        val persistentAssignments =
            pAssignments.asInstanceOf[List[(Var[Any], Option[Any], Boolean)]]
                .filter(p => !p._1.isTransient && !p._1.isLazyFlag)
        val (deleteAssignments, modifyAssignments) = persistentAssignments.partition(_._3)
        val deleteAssignmentsNormalized = normalize(deleteAssignments)
        val (insertStatementsNormalized, updateStatementsNormalized) = normalize(modifyAssignments).partition(tuple => !tuple._1.isPersisted)
        (insertStatementsNormalized, updateStatementsNormalized, deleteAssignmentsNormalized)
    }

    protected def deferFor(duration: Duration)(entity: BaseEntity) =
        BaseEntity.deferReadValidationFor(duration, entity)

    def shouldValidateRead(entity: BaseEntity) =
        true

    private def entitiesReadVersions(transaction: Transaction) =
        if (OptimisticOfflineLocking.validateReads) {
            val vars = transaction.reads.asInstanceOf[ListBuffer[Var[Any]]].filter(_.isMutable)
            val result = new IdentityHashMap[BaseEntity, Long]
            if (!vars.isEmpty) {
                val entitiesRead =
                    vars.map(_.outerEntity)
                        .filter(_.shouldValidateRead)
                        .map(e => e.id -> e).toMap
                val nestedTransaction = new NestedTransaction(transaction)
                transactional(nestedTransaction) {
                    for ((id, entity) <- entitiesRead) {
                        entity.varNamed(OptimisticOfflineLocking.versionVarName).get.map { version =>
                            result.put(entity, version.asInstanceOf[Long])
                        }
                    }
                }
                nestedTransaction.rollback
            }
            result
        } else
            new IdentityHashMap[BaseEntity, Long]

    private def validateTransactionEnd(transaction: Transaction, entities: List[BaseEntity]) = {
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

    private def filterDeletes(
        transaction: Transaction,
        statements: ListBuffer[MassModificationStatement],
        deletesUnfiltered: IdentityHashMap[BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]]]) = {

        val persistentDeletes = deletesUnfiltered.filter(_._1.isPersisted)
        lazy val deletesByEntityClass = persistentDeletes.map(_._1).groupBy(_.getClass)
        val deletedByMassStatement =
            statements.map {
                _ match {
                    case massDelete: MassDeleteStatement =>
                        val nestedTransaction = new NestedTransaction(transaction)
                        try transactional(nestedTransaction) {
                            val entitySource = massDelete.from.entitySources.onlyOne
                            val entityClass = entitySource.entityClass
                            val storage = storageFor(entityClass)
                            if (!storage.isMemoryStorage)
                                deletesByEntityClass.get(entityClass).map {
                                    _.filter(entity => liveCache.executeCriteria(massDelete.where.valueOption)(Map(entitySource -> entity))).toList
                                }.getOrElse(List())
                            else List()
                        } finally
                            nestedTransaction.rollback
                    case other =>
                        List()
                }
            }.flatten

        // performance
        if (deletedByMassStatement.nonEmpty)
            persistentDeletes -- deletedByMassStatement
        else
            persistentDeletes
    }

    private def twoPhaseCommit(
        readsByStorage: Map[Storage[Any], List[(BaseEntity, Long)]],
        statementsByStorage: Map[Storage[Any], List[MassModificationStatement]],
        insertsByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        updatesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        deletesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        storages: List[net.fwbrasil.activate.storage.Storage[Any]]) = {

        val storagesTransactionHandles = MutableMap[Storage[Any], Option[TransactionHandle]]()

        try {
            prepareCommit(
                readsByStorage,
                statementsByStorage,
                insertsByStorage,
                updatesByStorage,
                deletesByStorage,
                storages,
                storagesTransactionHandles)
            commit(storagesTransactionHandles)
        } catch {
            case e: Throwable =>
                rollbackStorages(
                    insertsByStorage,
                    updatesByStorage,
                    deletesByStorage,
                    storagesTransactionHandles)
                throw e
        }
    }

    private def extendedCommit(
        readsByStorage: Map[Storage[Any], List[(BaseEntity, Long)]],
        statementsByStorage: Map[Storage[Any], List[MassModificationStatement]],
        insertsByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        updatesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        deletesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        storages: List[net.fwbrasil.activate.storage.Storage[Any]]): Future[Unit] = {

        storages match {
            case storage :: tail =>
                storage.toStorageAsync(
                    readsByStorage(storage),
                    statementsByStorage(storage),
                    mapVarsToName(insertsByStorage(storage)),
                    mapVarsToName(updatesByStorage(storage)),
                    mapVarsToName(deletesByStorage(storage))).flatMap { _ =>
                        extendedCommit(
                            readsByStorage,
                            statementsByStorage,
                            insertsByStorage,
                            updatesByStorage,
                            deletesByStorage,
                            tail).recover {
                                case e: Throwable =>
                                    rollbackStorage(
                                        insertsByStorage,
                                        updatesByStorage,
                                        deletesByStorage,
                                        storage)
                                    throw e
                            }
                    }
            case Nil =>
                Future.successful()
        }
    }

    private def valueToRollback(ref: Var[Any], updatedValue: EntityValue[_]) = {
        ref.tval(
            if (ref.name != OptimisticOfflineLocking.versionVarName)
                ref.snapshotWithoutTransaction
            else
                updatedValue match {
                    case LongEntityValue(Some(version: Long)) =>
                        Some(version + 1)
                })
    }

    private def rollbackStorages(
        insertsByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        updatesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        deletesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        storagesTransactionHandles: MutableMap[Storage[Any], Option[TransactionHandle]]): Unit = {
        for ((storage, handle) <- storagesTransactionHandles) {
            handle.map(_.rollback).getOrElse {
                // "Manual" rollback for non-transactional storages
                rollbackStorage(
                    insertsByStorage,
                    updatesByStorage,
                    deletesByStorage,
                    storage)
            }
        }
    }

    private def rollbackStorage(
        insertsByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        updatesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        deletesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        storage: Storage[Any]): Unit = {
        val deletes = mapVarsToName(insertsByStorage(storage).map(
            tuple => (tuple._1, tuple._2.filter(tuple => tuple._1.name != OptimisticOfflineLocking.versionVarName))))
        val updates = mapVarsToName(updatesByStorage(storage).map(
            tuple => (tuple._1, tuple._2.map(tuple => (tuple._1, valueToRollback(tuple._1, tuple._2))))))
        val inserts = mapVarsToName(deletesByStorage(storage))
        try
            storage.toStorage(
                List(),
                List(),
                inserts,
                updates,
                deletes).map(_.commit)
        catch {
            case ex: Throwable =>
                writeRollbackErrorDumpFile(
                    ex,
                    inserts,
                    updates,
                    deletes)
        }
    }

    private def writeRollbackErrorDumpFile(
        exception: Throwable,
        inserts: List[(BaseEntity, Map[String, EntityValue[Any]])],
        updates: List[(BaseEntity, Map[String, EntityValue[Any]])],
        deletes: List[(BaseEntity, Map[String, EntityValue[Any]])]) =
        try {
            val bytes =
                this.defaultSerializer.toSerialized(
                    Map("exception" -> exception, "deletes" -> deletes, "updates" -> updates, "inserts" -> inserts))
            val file = new File(s"rollback-error-$contextName-${System.currentTimeMillis}-${UUIDUtil.generateUUID}.log")
            val fos = new FileOutputStream(file)
            fos.write(bytes)
            fos.close
            error(s"Cannot rollback storage. See ${file.getAbsolutePath}", exception)
        } catch {
            case e: Throwable =>
                error(s"Cannot rollback storage. Cannot write rollback log file.", e)
        }

    private def prepareCommit(
        readsByStorage: Map[Storage[Any], List[(BaseEntity, Long)]],
        statementsByStorage: Map[Storage[Any], List[MassModificationStatement]],
        insertsByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        updatesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        deletesByStorage: Map[Storage[Any], List[(BaseEntity, IdentityHashMap[Var[Any], EntityValue[Any]])]],
        storages: List[Storage[Any]],
        storagesTransactionHandles: MutableMap[Storage[Any], Option[TransactionHandle]]) =
        for (storage <- storages) {
            storagesTransactionHandles +=
                storage -> storage.toStorage(
                    readsByStorage(storage),
                    statementsByStorage(storage),
                    mapVarsToName(insertsByStorage(storage)),
                    mapVarsToName(updatesByStorage(storage)),
                    mapVarsToName(deletesByStorage(storage)))
        }

    private def sortStorages(storages: List[Storage[Any]]) =
        storages.sortWith((storageA, storageB) => {
            val priorityA = storagePriority(storageA)
            val priorityB = storagePriority(storageB)
            if (priorityA < priorityB)
                true
            else if (priorityA == priorityB)
                storageA.getClass.getName < storageB.getClass.getName
            else
                false
        })

    private def storagePriority(storage: Storage[Any]) =
        if (storage == this.storage)
            0
        else if (storage.isTransactional)
            1
        else if (storage.isMemoryStorage)
            2
        else if (storage.isSchemaless)
            3
        else
            4

    private def commit(storagesTransactionHandles: MutableMap[Storage[Any], Option[TransactionHandle]]) =
        for ((storage, handle) <- storagesTransactionHandles.toMap) {
            handle.map { h =>
                try
                    h.commit
                finally
                    storagesTransactionHandles -= storage
            }
        }

}