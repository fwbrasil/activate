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
import net.fwbrasil.activate.serialization.Serializator
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.serialization.SerializationContext
import net.fwbrasil.activate.util.CollectionUtil
import net.fwbrasil.activate.migration.MigrationContext

trait ActivateContext
        extends EntityContext
        with QueryContext
        with MassUpdateContext
        with MassDeleteContext
        with NamedSingletonSerializable
        with Logging
        with DelayedInit
        with DurableContext
        with StatementsContext
        with SerializationContext
        with MigrationContext {

    info("Initializing context " + contextName)

    private[activate] val liveCache = new LiveCache(this)
    private[activate] val properties =
        new ActivateProperties(None, "activate")

    implicit val context = this: this.type

    val storage: Storage[_]

    protected def storageFor(entity: Entity) =
        storage

    protected def storages =
        List[Storage[_]](storage)

    def reinitializeContext =
        logInfo("reinitializing context " + contextName) {
            liveCache.reinitialize
            clearStatements
            storages.foreach(_.reinitialize)
            reinitializeCoordinator
        }

    def currentTransaction =
        transactionManager.getRequiredActiveTransaction

    private[activate] def name = contextName

    lazy val contextName =
        this.getClass.getSimpleName.split('$').last

    private[activate] def initialize[E <: Entity](entity: E) =
        liveCache.initialize(entity)

    def acceptEntity[E <: Entity](entityClass: Class[E]) =
        contextEntities.map(_.contains(entityClass)).getOrElse(true)

    protected val contextEntities: Option[List[Class[_ <: Entity]]] = None

    override def toString = "ActivateContext: " + name

}

object ActivateContext {

    private[activate] var useContextCache = true

    private[activate] var currentClassLoader = this.getClass.getClassLoader

    private[activate] def loadClass(className: String) =
        classLoaderFor(className).loadClass(className)

    private[activate] def classLoaderFor(className: String) =
        if (className.startsWith("net.fwbrasil.activate"))
            classOf[Entity].getClassLoader()
        else
            currentClassLoader

    private[activate] val contextCache =
        new MutableHashMap[Class[_], ActivateContext]() with SynchronizedMap[Class[_], ActivateContext]

    private[activate] def contextFor[E <: Entity](entityClass: Class[E]) =
        if (!useContextCache)
            context(entityClass)
        else
            contextCache.getOrElseUpdate(entityClass, context(entityClass))

    def clearContextCache =
        contextCache.clear

    private def context[E <: Entity](entityClass: Class[E]) =
        instancesOf[ActivateContext]
            .filter(_.acceptEntity(entityClass))
            .onlyOne("\nThere should be one and only one context that accepts " + entityClass + ".\n" +
                "Maybe the context isn't initialized or you must override the contextEntities val on your context.\n" +
                "Important: The context definition must be declared in a base package of the entities packages.\n" +
                "Example: com.app.myContext for com.app.model.MyEntity")

}
