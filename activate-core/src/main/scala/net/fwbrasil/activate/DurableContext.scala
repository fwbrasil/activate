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

trait DurableContext {
	this: ActivateContext =>

	val contextUniqueId = UUIDUtil.generateUUID

	override def makeDurable(transaction: Transaction) = {
		val refsAssignments = transaction.refsAssignments
		val statements = statementsForTransaction(transaction)
		if (refsAssignments.nonEmpty || statements.nonEmpty) {
			val (assignments, deletes) = filterVars(refsAssignments)
			val assignmentsEntities = assignments.map(_._1.outerEntity)
			val deletedEntities = deletes.map(_._1)
			validateTransactionEnd(transaction, assignmentsEntities ::: deletedEntities)
			storage.toStorage(statements.toList, assignments, deletes)
			setPersisted(assignmentsEntities)
			deleteFromLiveCache(deletedEntities)
			statementsForTransaction(transaction).clear
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