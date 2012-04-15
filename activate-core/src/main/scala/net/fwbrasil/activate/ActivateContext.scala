package net.fwbrasil.activate

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.EntityContext
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.query.QueryContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.query.Query
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.serialization.NamedSingletonSerializable
import net.fwbrasil.activate.serialization.NamedSingletonSerializable.instancesOf
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.query.QueryNormalizer
import scala.collection.mutable.{ Map => MutableMap, Set => MutableSet }
import net.fwbrasil.activate.entity.EntityValue
import java.util.IdentityHashMap

trait ActivateContext
		extends EntityContext
		with QueryContext
		with NamedSingletonSerializable
		with Logging {

	info("Initializing context " + contextName)

	EntityHelper.initialize(this.getClass)

	private[activate] val liveCache = new LiveCache(this)

	implicit val context = this

	val storage: Storage

	protected def storageFor(entity: Entity) =
		storage
	
	protected def storages =
		List(storage)

	def reinitializeContext =
		logInfo("reinitializing context " + contextName) {
			liveCache.reinitialize
			storages.foreach(_.reinitialize)
		}

	private[activate] def executeQuery[S](query: Query[S], iniatializing: Boolean): List[S] =
		logInfo("executing query " + query.toString) {
			(for (normalized <- QueryNormalizer.normalize(query)) yield {
				logInfo("executing normalized query " + normalized.toString) {
					QueryNormalizer.denormalizeSelectWithOrderBy(query, liveCache.executeQuery(normalized, iniatializing))
				}
			}).flatten
		}

	private[activate] def name = contextName
	def contextName: String

	private[activate] def initialize[E <: Entity](entity: E) =
		liveCache.initialize(entity)

	private[activate] def initializeGraphIfNecessary(entities: Seq[Entity]) {

	}

	override def makeDurable(transaction: Transaction) = {
		val (assignments, deletes) = filterVars(transaction.refsAssignments)
		storage.toStorage(assignments, deletes)
		setPersisted(assignments)
		deleteFromLiveCache(deletes)
	}

	private[this] def setPersisted(assignments: List[(Var[Any], EntityValue[Any])]) =
		for ((ref, value) <- assignments)
			yield ref.outerEntity.setPersisted

	private[this] def deleteFromLiveCache(deletes: List[(Entity, Map[Var[Any], EntityValue[Any]])]) =
		for ((entity, map) <- deletes)
			liveCache.delete(entity)

	private[this] def filterVars(pAssignments: List[(Ref[Any], (Option[Any], Boolean))]) = {
		val varAssignments = pAssignments.filter(_._1.isInstanceOf[Var[_]]).asInstanceOf[List[(Var[Any], (Option[Any], Boolean))]]
		val assignments = MutableMap[Var[Any], EntityValue[Any]]()
		val deletes = MutableMap[String, MutableMap[Var[Any], EntityValue[Any]]]()
		val entityMap = MutableMap[String, Entity]()
		for ((ref, (value, destroyed)) <- varAssignments; if (ref.outerEntity != null)) {
			if (destroyed) {
				if (ref.outerEntity.isPersisted) {
					deletes.getOrElseUpdate(ref.outerEntity.id, MutableMap[Var[Any], EntityValue[Any]]()) += Tuple2(ref, ref.tval(ref.refContent.value))
					entityMap += (ref.outerEntity.id -> ref.outerEntity)
				}
			} else
				assignments += Tuple2(ref, ref.toEntityPropertyValue(value.getOrElse(null)))
		}
		val deleteList =
			for ((entityId, properties) <- deletes.toList)
				yield (entityMap(entityId), properties.toMap)
		(assignments.toList, deleteList)
	}

	protected[activate] def acceptEntity[E <: Entity](entityClass: Class[E]) =
		true

	override def toString = "ActivateContext: " + name

}

object ActivateContext {

	private[activate] def contextFor[E <: Entity](entityClass: Class[E]) =
		instancesOf[ActivateContext]
			.filter(_.acceptEntity(entityClass))
			.onlyOne("There should be only one context that accept " + entityClass + ". Override acceptEntity on your context.")

}
