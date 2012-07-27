package net.fwbrasil.activate

import java.util.IdentityHashMap
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.EntityValidation
import net.fwbrasil.radon.transaction.NestedTransaction

trait DurableContext {
	this: ActivateContext =>

	override def makeDurable(transaction: Transaction) = {
		val refsAssignments = transaction.refsAssignments
		val statements = statementsForTransaction(transaction)
		if (refsAssignments.nonEmpty || statements.nonEmpty) {
			val (assignments, deletes) = filterVars(refsAssignments)
			validateTransactionEnd(transaction, assignments, deletes)
			storage.toStorage(statements.toList, assignments, deletes)
			setPersisted(assignments)
			deleteFromLiveCache(deletes)
			statementsForTransaction(transaction).clear
		}
	}

	private[this] def setPersisted(assignments: List[(Var[Any], EntityValue[Any])]) =
		for ((ref, value) <- assignments)
			yield ref.outerEntity.setPersisted

	private[this] def deleteFromLiveCache(deletes: List[(Entity, List[(Var[Any], EntityValue[Any])])]) =
		for ((entity, map) <- deletes)
			liveCache.delete(entity)

	private[this] def filterVars(pAssignments: List[(Ref[Any], (Option[Any], Boolean))]) = {
		// Assume that all assignments are of Vars for performance reasons (could be Ref)
		val varAssignments = pAssignments.asInstanceOf[List[(Var[Any], (Option[Any], Boolean))]]
		val assignments = new IdentityHashMap[Var[Any], EntityValue[Any]]()
		val deletes = new IdentityHashMap[Entity, IdentityHashMap[Var[Any], EntityValue[Any]]]()
		for ((ref, (value, destroyed)) <- varAssignments; if (ref.outerEntity != null)) {
			if (destroyed) {
				if (ref.outerEntity.isPersisted) {
					val propertiesMap =
						Option(deletes.get(ref.outerEntity)).getOrElse {
							val map = new IdentityHashMap[Var[Any], EntityValue[Any]]()
							deletes.put(ref.outerEntity, map)
							map
						}
					propertiesMap.put(ref, ref.tval(ref.refContent.value))
				}
			} else
				assignments.put(ref, ref.toEntityPropertyValue(value.getOrElse(null)))
		}
		import scala.collection.JavaConversions._
		(assignments.toList, deletes.toList.map(tuple => (tuple._1, tuple._2.toList)))
	}

	private def validateTransactionEnd(transaction: Transaction, assignments: List[(Var[Any], EntityValue[Any])], deletes: List[(Entity, List[(Var[Any], EntityValue[Any])])]) = {
		val nestedTransaction = new NestedTransaction(transaction)
		try transactional(nestedTransaction) {
			val transactionEntities = assignments.map(_._1.outerEntity) ++ deletes.map(_._1)
			EntityValidation.validateOnTransactionEnd(transactionEntities, transaction)
		} finally
			nestedTransaction.rollback
	}
}