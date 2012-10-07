package net.fwbrasil.activate

import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.statement.mass.MassDeleteContext
import net.fwbrasil.activate.entity.EntityContext
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.statement.query.QueryContext
import net.fwbrasil.activate.statement.mass.MassUpdateContext
import net.fwbrasil.activate.statement.query.QueryNormalizer
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.query.Query
import scala.collection.mutable.SynchronizedMap
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.serialization.NamedSingletonSerializable
import scala.collection.mutable.{ HashMap => MutableHashMap }
import net.fwbrasil.activate.serialization.NamedSingletonSerializable.instancesOf
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.coordinator.Coordinator

trait ActivateContext
		extends EntityContext
		with QueryContext
		with MassUpdateContext
		with MassDeleteContext
		with NamedSingletonSerializable
		with Logging
		with DelayedInit
		with DurableContext
		with StatementsContext {

	info("Initializing context " + contextName)

	ActivateContext.clearContextCaches
	EntityHelper.initialize(this.getClass)

	private[activate] val liveCache = new LiveCache(this)
	private[activate] val properties =
		new ActivateProperties(None, "activate")

	implicit val context = this: this.type

	val storage: Storage[_]

	protected def storageFor(entity: Entity) =
		storage

	protected def storages =
		List(storage)

	def reinitializeContext =
		logInfo("reinitializing context " + contextName) {
			liveCache.reinitialize
			clearStatements
			storages.foreach(_.reinitialize)
			reinitializeCoordinator
		}

	def currentTransaction =
		transactionManager.getRequiredActiveTransaction

	private[activate] def executeQuery[S](query: Query[S], iniatializing: Boolean): List[S] = {
		(for (normalized <- QueryNormalizer.normalize[Query[S]](query)) yield {
			QueryNormalizer.denormalizeSelectWithOrderBy(query, liveCache.executeQuery(normalized, iniatializing))
		}).flatten
	}

	private[activate] def name = contextName

	lazy val contextName =
		this.getClass.getSimpleName.split('$').last

	private[activate] def initialize[E <: Entity](entity: E) =
		liveCache.initialize(entity)

	protected[activate] def acceptEntity[E <: Entity](entityClass: Class[E]) =
		contextEntities.map(_.contains(entityClass)).getOrElse(true)

	protected val contextEntities: Option[List[Class[_ <: Entity]]] = None

	protected[activate] def execute(action: StorageAction) =
		storage.migrate(action)

	protected[activate] def runMigration =
		Migration.update(this)

	protected lazy val runMigrationAtStartup = true

	def delayedInit(x: => Unit): Unit = {
		x
		if (runMigrationAtStartup)
			if (!coordinatorClientOption.isDefined || Coordinator.isServerVM)
				runMigration
			else
				warn("Migrations will not run. If there is a coordinator, only the coordinator instance can run migrations.")
	}

	protected[activate] def entityMaterialized(entity: Entity) = {}

	override def toString = "ActivateContext: " + name

}

object ActivateContext {

	private[activate] var useContextCache = true

	private[activate] val contextCache =
		new MutableHashMap[Class[_], ActivateContext]() with SynchronizedMap[Class[_], ActivateContext]

	private[activate] def contextFor[E <: Entity](entityClass: Class[E]) =
		if (!useContextCache)
			context(entityClass)
		else
			contextCache.getOrElseUpdate(entityClass, context(entityClass))

	def clearContextCaches = {
		contextCache.clear
		Migration.migrationsCache.clear
		Migration.storageVersionCache.clear
	}

	private def context[E <: Entity](entityClass: Class[E]) =
		instancesOf[ActivateContext]
			.filter(_.acceptEntity(entityClass))
			.onlyOne("\nThere should be one and only one context that accepts " + entityClass + ".\n" +
				"Maybe the context isn't initialized or you must override the contextEntities val on your context.\n" +
				"Important: The context definition must be declared in a base package of the entities packages.\n" +
				"Example: com.app.myContext for com.app.model.MyEntity")

}
