package net.fwbrasil.activate

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.EntityContext
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.statement.query.QueryContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.serialization.NamedSingletonSerializable
import net.fwbrasil.activate.serialization.NamedSingletonSerializable.instancesOf
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.statement.query.QueryNormalizer
import scala.collection.mutable.{ Map => MutableMap, Set => MutableSet, HashMap => MutableHashMap }
import net.fwbrasil.activate.entity.EntityValue
import java.util.IdentityHashMap
import net.fwbrasil.activate.migration.MigrationAction
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.radon.util.ReferenceWeakKeyMap
import net.fwbrasil.activate.statement.Statement
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.statement.mass.MassUpdateContext
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.mass.MassDeleteContext
import scala.collection.mutable.SynchronizedMap

trait ActivateContext
		extends EntityContext
		with QueryContext
		with MassUpdateContext
		with MassDeleteContext
		with NamedSingletonSerializable
		with Logging
		with DelayedInit {

	info("Initializing context " + contextName)

	EntityHelper.initialize(this.getClass)

	private[activate] val liveCache = new LiveCache(this)
	private[activate] val properties =
		new ActivateProperties(None, "activate")

	private val transactionStatements =
		ReferenceWeakKeyMap[Transaction, ListBuffer[MassModificationStatement]]()

	implicit val context = this

	val storage: Storage

	protected def storageFor(entity: Entity) =
		storage

	protected def storages =
		List(storage)

	def reinitializeContext =
		logInfo("reinitializing context " + contextName) {
			liveCache.reinitialize
			transactionStatements.clear
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

	private[activate] def currentTransactionStatements =
		transactionManager.getActiveTransaction.map(statementsForTransaction).getOrElse(ListBuffer())

	private def statementsForTransaction(transaction: Transaction) =
		transactionStatements.getOrElseUpdate(transaction, ListBuffer())

	private[activate] def executeMassModification(statement: MassModificationStatement) = {
		liveCache.executeMassModification(statement)
		currentTransactionStatements += statement
	}

	private[activate] def name = contextName
	def contextName: String

	private[activate] def initialize[E <: Entity](entity: E) =
		liveCache.initialize(entity)

	private[activate] def initializeGraphIfNecessary(entities: Seq[Entity]) {

	}

	override def makeDurable(transaction: Transaction) = {
		val (assignments, deletes) = filterVars(transaction.refsAssignments)
		val statements = statementsForTransaction(transaction)
		storage.toStorage(statements.toList, assignments, deletes)
		setPersisted(assignments)
		deleteFromLiveCache(deletes)
		statementsForTransaction(transaction).clear
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

	protected[activate] def execute(action: MigrationAction) =
		storage.migrate(action)

	protected[activate] def runMigration =
		Migration.update(this)

	protected lazy val runMigrationAtStartup = true

	def delayedInit(x: => Unit): Unit = {
		x
		if (runMigrationAtStartup)
			runMigration
	}

	protected[activate] def entityMaterialized(entity: Entity) = {}

	override def toString = "ActivateContext: " + name

}

object ActivateContext {

	private[activate] val contextCache =
		new MutableHashMap[Class[_], ActivateContext]() with SynchronizedMap[Class[_], ActivateContext]

	private[activate] def contextFor[E <: Entity](entityClass: Class[E]) = {
		contextCache.getOrElseUpdate(entityClass,
			instancesOf[ActivateContext]
				.filter(_.acceptEntity(entityClass))
				.onlyOne("There should be only one context that accept " + entityClass + ". Override acceptEntity on your context."))
	}

}
