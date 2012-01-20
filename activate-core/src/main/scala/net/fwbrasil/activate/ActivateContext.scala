package net.fwbrasil.activate

import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.radon.ref.RefContext
import net.fwbrasil.radon.transaction.TransactionContext
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query._
import net.fwbrasil.activate.serialization._
import net.fwbrasil.activate.storage._
import net.fwbrasil.activate.cache.live._
import scala.collection.mutable.{ Map => MutableMap }
import java.io._
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.CollectionUtil.combine

trait ActivateContext
		extends EntityContext
		with QueryContext
		with Serializable
		with NamedSingletonSerializable
		with Logging {

	info("Initializing context " + contextName)

	EntityHelper.initialize

	var running = true

	def start = synchronized {
		running = true
	}

	def stop = synchronized {
		running = false
	}

	private[activate] val liveCache = new LiveCache(this)

	implicit val context = this

	val storage: Storage

	def reinitializeContext =
		logInfo("reinitializing context " + contextName) {
			liveCache.reinitialize
			storage.reinitialize
		}

	def executeQuery[S](query: Query[S]): List[S] =
		logInfo("executing query " + query.toString) {
			(for (normalized <- QueryNormalizer.normalize(query)) yield {
				logInfo("executing normalized query " + normalized.toString) {
					QueryNormalizer.denormalizeSelectWithOrderBy(query, liveCache.executeQuery(normalized))
				}
			}).flatten
		}

	def name = contextName
	def contextName: String

	def initialize(entity: Entity) =
		liveCache.initialize(entity)

	def allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
		query { (entity: E) =>
			where({
				var criteria = criterias(0)(entity)
				for (i <- 1 until criterias.size)
					criteria = criteria :&& criterias(i)(entity)
				criteria
			}) select (entity)
		}.execute

	def all[E <: Entity: Manifest] =
		allWhere[E](_ isSome)

	def byId[T <: Entity: Manifest](id: String): Option[T] = {
		val fromLiveCache = liveCache.byId[T](id)
		if (fromLiveCache.isDefined)
			if (fromLiveCache.get.isDeleted)
				None
			else
				fromLiveCache
		else allWhere[T](_ :== id).headOption
	}

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
		val assignments = MutableMap[Var[Any], EntityValue[Any]]()
		val deletes = MutableMap[Var[Any], EntityValue[Any]]()
		for ((ref, (value, destroyed)) <- varAssignments; if (ref.outerEntity != null)) {
			if (destroyed) {
				if (ref.outerEntity.isPersisted)
					deletes += Tuple2(ref, ref.toEntityPropertyValue(value.getOrElse(null)))
			} else
				assignments += Tuple2(ref, ref.toEntityPropertyValue(value.getOrElse(null)))
		}
		(assignments.toMap, deletes.toMap)
	}

	protected[activate] def acceptEntity(entityClass: Class[_ <: Entity]) =
		running

	override def toString = "ActivateContext: " + name

}

object ActivateContext {
	def contextFor(entityClass: Class[_ <: Entity]) = {
		val instances = NamedSingletonSerializable.instancesOf(classOf[ActivateContext])
		toRichList(for (
			ctx <- instances;
			if (ctx.acceptEntity(entityClass))
		) yield ctx).onlyOne
	}

}
