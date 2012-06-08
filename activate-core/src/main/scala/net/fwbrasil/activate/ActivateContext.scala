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

	EntityHelper.initialize(this.getClass)

	private[activate] val liveCache = new LiveCache(this)
	private[activate] val properties =
		new ActivateProperties(None, "activate")

	implicit val context = this

	val storage: Storage

	protected def storageFor(entity: Entity) =
		storage

	protected def storages =
		List(storage)

	def reinitializeContext =
		logInfo("reinitializing context " + contextName) {
			liveCache.reinitialize
			clearStatements
			storages.foreach(_.reinitialize)
		}

	private[activate] def executeQuery[S](query: Query[S], iniatializing: Boolean): List[S] =
		(for (normalized <- QueryNormalizer.normalize[Query[S]](query)) yield {
			QueryNormalizer.denormalizeSelectWithOrderBy(query, liveCache.executeQuery(normalized, iniatializing))
		}).flatten

	private[activate] def name = contextName
	def contextName: String

	private[activate] def initialize[E <: Entity](entity: E) =
		liveCache.initialize(entity)

	protected[activate] def acceptEntity[E <: Entity](entityClass: Class[E]) =
		true

	protected[activate] def execute(action: StorageAction) =
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

	private[activate] def contextFor[E <: Entity](entityClass: Class[E]) =
		contextCache.getOrElseUpdate(entityClass,
			instancesOf[ActivateContext]
				.filter(_.acceptEntity(entityClass))
				.onlyOne("There should be only one context that accept " + entityClass + ". Override acceptEntity on your context."))

	def clearContextCache =
		contextCache.clear

}
