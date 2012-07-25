package net.fwbrasil.activate

import java.util.IdentityHashMap
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.EntityValidation

trait DurableContext {
	this: ActivateContext =>

	override def makeDurable(transaction: Transaction) = {
		val refsAssignments = transaction.refsAssignments
		val statements = statementsForTransaction(transaction)
		if (refsAssignments.nonEmpty || statements.nonEmpty) {
			val (assignments, deletes) = filterVars(refsAssignments)
			val assignmentsEntities = assignments.map(_._1.outerEntity)
			val deletesEntities = deletes.map(_._1)
			EntityValidation.validateOnTransactionEnd(assignmentsEntities ++ deletesEntities, transaction)
			storage.toStorage(statements.toList, assignments, deletes)
			setPersisted(assignmentsEntities)
			deleteFromLiveCache(assignmentsEntities)
			statementsForTransaction(transaction).clear
		}
	}

	private[this] def setPersisted(entities: List[Entity]) =
		entities.foreach(_.setPersisted)

	private[this] def deleteFromLiveCache(entities: List[Entity]) =
		entities.foreach(liveCache.delete)

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
}