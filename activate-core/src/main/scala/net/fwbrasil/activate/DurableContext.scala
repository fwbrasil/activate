package net.fwbrasil.activate

import java.util.IdentityHashMap
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.EntityValidation
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.radon.transaction.NestedTransaction
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.coordinator.coordinatorObject
import net.fwbrasil.radon.ConcurrentTransactionException
import net.fwbrasil.radon.transaction.TransactionManager

class ActivateConcurrentTransactionException(val entitiesIds: Set[String], refs: Ref[_]*) extends ConcurrentTransactionException(refs: _*)

trait DurableContext {
	this: ActivateContext =>

	private val contextId = UUIDUtil.generateUUID

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

	private lazy val coordinatorOption =
		if (storage.isMemoryStorage)
			None
		else
			Option(coordinatorObject)

	protected def startCoordinator =
		coordinatorOption.map(coordinator => {
			coordinator.registerContext(contextId)
			Runtime.getRuntime.removeShutdownHook(new Thread {
				override def run = coordinator.deregisterContext(contextId)
			})
		})

	private def reloadEntities(ids: Set[String]) = {
		liveCache.unitializeLazyEntities(ids)
		coordinatorObject.removeNotifications(contextId, ids)
	}

	private def runWithCoordinatorIfDefined(entitiesIds: => Set[String])(f: => Unit) =
		coordinatorOption.map { coordinator =>

			val failed = coordinator.tryLock(contextId, entitiesIds)
			if (failed.nonEmpty)
				throw new ActivateConcurrentTransactionException(failed)
			try
				f
			finally
				coordinator.unlock(contextId, entitiesIds)

		}.getOrElse(f)

	override def makeDurable(transaction: Transaction) = {
		lazy val statements = statementsForTransaction(transaction)

		val (assignments, deletes) = filterVars(transaction.assignments)

		val assignmentsEntities = assignments.map(_._1.outerEntity)
		val deletedEntities = deletes.map(_._1)
		val entities = assignmentsEntities ::: deletedEntities

		lazy val entitiesIds = (entities.map(_.id) ++ transaction.reads.map(_.asInstanceOf[Var[_]].outerEntity.id)).toSet

		runWithCoordinatorIfDefined(entitiesIds) {
			if (assignments.nonEmpty || deletes.nonEmpty || statements.nonEmpty) {
				validateTransactionEnd(transaction, entities)
				storage.toStorage(statements.toList, assignments, deletes)
				setPersisted(assignmentsEntities)
				deleteFromLiveCache(deletedEntities)
				statements.clear
			}
		}
	}

	private[this] def setPersisted(entities: List[Entity]) =
		entities.foreach(_.setPersisted)

	private[this] def deleteFromLiveCache(entities: List[Entity]) =
		entities.foreach(liveCache.delete)

	private[this] def filterVars(pAssignments: List[(Ref[Any], Option[Any], Boolean)]) = {
		// Assume that all assignments are of Vars for performance reasons (could be Ref)
		val varAssignments = pAssignments.asInstanceOf[List[(Var[Any], Option[Any], Boolean)]]

		val (assignmentsDelete, assignmentsUpdate) = varAssignments.map(e => (e._1, e._1.tval(e._2), e._3)).partition(_._3)

		val deletes = new IdentityHashMap[Entity, ListBuffer[(Var[Any], EntityValue[Any])]]()

		for ((ref, value, destroyed) <- assignmentsDelete) {
			val entity = ref.outerEntity
			if (entity.isPersisted) {
				if (!deletes.containsKey(entity))
					deletes.put(entity, ListBuffer())
				deletes.get(entity) += (ref -> value)
			}
		}

		import scala.collection.JavaConversions._
		(assignmentsUpdate.map(e => (e._1, e._2)),
			deletes.toList.map(tuple => (tuple._1, tuple._2.toList)))
	}

	private def validateTransactionEnd(transaction: Transaction, entities: List[Entity]) = {
		val toValidate = entities.filter(EntityValidation.validatesOnTransactionEnd(_, transaction))
		if (toValidate.nonEmpty) {
			val nestedTransaction = new NestedTransaction(transaction)
			try transactional(nestedTransaction) {
				toValidate.foreach(_.validate)
			} finally
				nestedTransaction.rollback
		}
	}
}