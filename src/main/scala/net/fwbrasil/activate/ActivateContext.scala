package net.fwbrasil.activate

import net.fwbrasil.radon.ref.RefContext
import net.fwbrasil.radon.transaction.TransactionContext
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query._
import net.fwbrasil.activate.serialization._
import net.fwbrasil.activate.storage._
import net.fwbrasil.activate.cache.live._
import scala.collection.mutable.{ Set => MutableSet }
import java.io._

trait ActivateContext
	extends EntityContext
	with QueryContext
	with Serializable
	with NamedSingletonSerializable
	with RefContext {

	private[activate] lazy val liveCache = new LiveCache(this)

	implicit val context = this

	val storage: Storage
	
	def reinitializeContext = {
		liveCache.reinitialize
		storage.reinitialize
	}
	
	def executeQuery[S](query: Query[S]): List[S] =
		liveCache.executeQuery(query)
	
	def name = contextName
	def contextName: String

	def initialize(entity: Entity) =
		liveCache.initialize(entity)

	implicit def valueToVar[A](value: A)(implicit m: Manifest[A], tval: Option[A] => EntityValue[A]): Var[A] =
		new Var(value)(m, tval, this)

	implicit def valueToVar[A](value: Option[A])(implicit m: Manifest[A], tval: Option[A] => EntityValue[A]): Var[A] =
		new Var[A](value)(m, tval, this)

	def allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
		query { (entity: E) =>
			where({
				var criteria = criterias(0)(entity)
				for (i <- 1 until criterias.size)
					criteria = criteria :&& criterias(i)(entity)
				criteria
			}) select (entity)
		}.execute.mapConserve(_._1.get).asInstanceOf[List[E]]

	def all[E <: Entity: Manifest] =
		allWhere[E](_ isSome)
	
	def byId[E <: Entity: Manifest](id: String) =
		allWhere[E](_ :== id).headOption

	override def makeDurable(transaction: Transaction) = {
		val (assignments, deletes) = filterVars(transaction.refsAssignments)
		storage.toStorage(assignments, deletes)
		for ((ref, value) <- assignments)
			ref.outerEntity.setPersisted
		for ((ref, value) <- deletes)
			liveCache.delete(ref.outerEntity)
	}

	def filterVars(pAssignments: Set[(Ref[Any], (Option[Any], Boolean))]) = {
		val varAssignments = pAssignments.filter(_._1.isInstanceOf[Var[_]]).asInstanceOf[Set[(Var[Any], (Option[Any], Boolean))]]
		val assignments = MutableSet[Tuple2[Var[Any], EntityValue[Any]]]()
		val deletes = MutableSet[Tuple2[Var[Any], EntityValue[Any]]]()
		for ((ref, (value, destroyed)) <- varAssignments) {
			if (destroyed) {
				if (ref.outerEntity.isPersisted)
					deletes += Tuple2(ref, ref.toEntityPropertyValue(value.getOrElse(null)))
			} else
				assignments += Tuple2(ref, ref.toEntityPropertyValue(value.getOrElse(null)))
		}
		(assignments.toMap, deletes.toMap)
	}
	
	override def toString = "ActivateContext: " + name

}

