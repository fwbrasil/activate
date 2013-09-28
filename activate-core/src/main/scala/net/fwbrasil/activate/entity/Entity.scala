package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.radon.transaction.TransactionContext
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.util.RichList._
import java.lang.reflect.Field
import java.lang.reflect.Method
import scala.collection.mutable.{ Map => MutableMap, HashSet => MutableHashSet }
import java.util.Date
import org.joda.time.DateTime
import java.util.{ HashMap => JHashMap }
import net.fwbrasil.activate.OptimisticOfflineLocking
import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.cache.CacheType
import net.fwbrasil.activate.cache.CustomCache
import net.fwbrasil.activate.entity.map.EntityMap
import net.fwbrasil.activate.statement.StatementMocks

trait Entity extends Serializable with EntityValidation with EntityListeners {

    @transient
    private var _baseVar: Var[Any] = null

    private[activate] var _varsMap: JHashMap[String, Var[Any]] = null

    @transient
    private var _vars: List[Var[Any]] = null

    final var version: Long = 0l

    private[activate] def varsMap =
        _varsMap

    private[activate] def buildVarsMap = {
        // Implementation injected by EntityEnhancer
    }

    private[activate] def putVar(name: String, ref: Var[Any]) =
        _varsMap.put(name.split('$').last, ref)

    def vars = {
        if (_vars == null) {
            import scala.collection.JavaConversions._
            _vars = varsMap.values.toList.filter(!_.isLazyFlag)
        }
        _vars
    }

    private def baseVar = {
        if (_baseVar == null)
            _baseVar = vars.head
        _baseVar
    }

    private[activate] def postConstruct = {
        buildVarsMap
        validateOnCreate
        addToLiveCache
        initializeListeners
    }

    def isDeleted =
        baseVar.isDestroyed

    private[activate] def isDeletedSnapshot =
        baseVar.isDestroyedSnapshot

    def isDirty =
        vars.find(ref =>
            ref.isDirty && !OptimisticOfflineLocking.isVersionVar(ref))
            .isDefined

    final val id: String = null

    def delete = {
        beforeDelete
        baseVar.destroy
        for (ref <- vars; if (ref != _baseVar))
            ref.destroy
        afterDelete
    }

    def deleteCascade: Unit = {
        this.delete
        references.values.foreach(_.foreach(_.deleteCascade))
    }

    def deleteIfHasntReferences =
        if (!canDelete)
            throw new CannotDeleteEntity(references)
        else
            delete

    def canDelete =
        references.find(_._2.filter(_ != this).nonEmpty).isEmpty

    def references =
        EntityHelper.getEntityMetadata(this.getClass).references.mapValues {
            references =>
                val ctx = context
                ctx.select[Entity](manifestClass(references.head.entityMetadata.entityClass)).where { entity =>
                    import ctx._
                    var criteria: Criteria = (entity.varNamed(references.head.name).get :== this)
                    for (reference <- references.tail)
                        criteria = criteria :|| (entity.varNamed(reference.name).get :== this)
                    criteria
                }.filter(!_.isDeleted)
        }

    def toMap =
        new EntityMap[this.type](this.asInstanceOf[this.type])(manifest[this.type], context)

    private[activate] def deleteWithoutInitilize = {
        baseVar.destroyWithoutInitilize
        for (ref <- vars; if (ref != _baseVar))
            ref.destroyWithoutInitilize
    }

    def creationTimestamp = UUIDUtil timestamp id.substring(0, 35)
    def creationDate = new Date(creationTimestamp)
    def creationDateTime = new DateTime(creationTimestamp)

    private var persistedflag = false
    private var initialized = false
    private var initializing = true

    private[activate] def setPersisted =
        persistedflag = true

    private[activate] def setNotPersisted =
        persistedflag = false

    def isPersisted =
        persistedflag

    private[activate] def setNotInitialized =
        initialized = false

    private[activate] def setInitializing =
        initializing = true

    private[activate] def setInitialized = {
        initializing = false
        initialized = true
    }

    private[activate] def isInitialized =
        initialized

    private[activate] def initialize(forWrite: Boolean) =
        if (!initializing) {
            this.synchronized {
                if (!initialized) {
                    beforeInitialize
                    context.liveCache.loadFromDatabase(this, withinTransaction = false)
                    initialized = true
                    afterInitialize
                }
            }
            if ((forWrite || OptimisticOfflineLocking.validateReads) &&
                OptimisticOfflineLocking.isEnabled && isPersisted) {
                val versionVar = _varsMap.get(OptimisticOfflineLocking.versionVarName).asInstanceOf[Var[Long]]
                if (!versionVar.isDirty)
                    versionVar.putValueWithoutInitialize(versionVar.getValueWithoutInitialize + 1l)
            }
        }

    private[activate] def reload =
        this.synchronized {
            context.liveCache.loadFromDatabase(this, withinTransaction = true)
            this
        }

    private[activate] def initializeGraph: Unit =
        initializeGraph(Set())

    private[activate] def initializeGraph(seen: Set[Entity]): Unit =
        this.synchronized {
            initialize(forWrite = false)
            if (!isDeletedSnapshot)
                for (ref <- varsOfTypeEntity)
                    if (ref.get.nonEmpty) {
                        val entity = ref.get.get
                        if (!seen.contains(entity))
                            entity.initializeGraph(seen + this)
                    }
        }

    private def varsOfTypeEntity =
        vars.filterByType[Entity, Var[Entity]]((ref: Var[Any]) => ref.valueClass)

    private[activate] def isInLiveCache =
        context.liveCache.contains(this)

    private[activate] def entityMetadata: EntityMetadata = {
        // Injected by EntityEnhancer
        null
    }

    private def varFields =
        entityMetadata.varFields

    private[activate] def context: ActivateContext =
        ActivateContext.contextFor(this.getClass)

    def varNamed(name: String) =
        varsMap.get(name)

    def originalValue[T](f: this.type => T) = {
        val name = StatementMocks.funcToVarName(f)
        varNamed(name).getOriginalValue.getOrElse(null).asInstanceOf[T]
    }

    private[activate] def addToLiveCache =
        context.liveCache.toCache(this)

    protected def toStringVars =
        vars

    override def toString =
        EntityHelper.getEntityName(this.getClass) + (
            if (Entity.toStringSeen(this))
                "(loop id->" + id + ")"
            else {
                val varsString =
                    if (initialized) {
                        context.transactional {
                            "(" + toStringVars.mkString(", ") + ")"
                        }
                    } else
                        "(uninitialized id->" + id + ")"
                Entity.toStringRemoveSeen(this)
                varsString
            })

    protected def writeReplace(): AnyRef =
        if (Entity.serializeUsingEvelope)
            new EntitySerializationEnvelope(this)
        else
            this

}

object Entity {

    var serializeUsingEvelope = true
    @transient
    private var _toStringLoopSeen: ThreadLocal[MutableHashSet[Entity]] = _
    private def toStringLoopSeen =
        synchronized {
            if (_toStringLoopSeen == null)
                _toStringLoopSeen = new ThreadLocal[MutableHashSet[Entity]]() {
                    override def initialValue = MutableHashSet[Entity]()
                }
            _toStringLoopSeen
        }
    def toStringSeen(entity: Entity) = {
        val set = toStringLoopSeen.get
        val ret = set.contains(entity)
        set += entity
        ret
    }
    def toStringRemoveSeen(entity: Entity) = //{}
        toStringLoopSeen.get -= entity

}

case class CannotDeleteEntity(references: Map[EntityMetadata, List[Entity]])
    extends Exception(s"Can't delete entity due references from ${references.keys.map(_.name)}.")

class EntitySerializationEnvelope[E <: Entity](entity: E) extends Serializable {
    val id = entity.id
    val context = entity.context
    protected def readResolve(): Any =
        context.liveCache.materializeEntity(id)
}

trait EntityContext extends ValueContext with TransactionContext with LazyListContext {
    this: ActivateContext =>

    EntityHelper.initialize(this.getClass)

    type Entity = net.fwbrasil.activate.entity.Entity
    type Alias = net.fwbrasil.activate.entity.InternalAlias @scala.annotation.meta.field
    type Var[A] = net.fwbrasil.activate.entity.Var[A]
    type EntityMap[E <: Entity] = net.fwbrasil.activate.entity.map.EntityMap[E]
    type MutableEntityMap[E <: Entity] = net.fwbrasil.activate.entity.map.MutableEntityMap[E]
    type Encoder[A, B] = net.fwbrasil.activate.entity.Encoder[A, B]

    protected def liveCacheType = CacheType.softReferences

    protected def customCaches: List[CustomCache[_]] = List()

    protected[activate] val liveCache = new LiveCache(this, liveCacheType, customCaches)

    protected[activate] def entityMaterialized(entity: Entity) = {}

    protected[activate] def hidrateEntities(entities: Iterable[Entity])(implicit context: ActivateContext) =
        for (entity <- entities) {
            initializeBitmaps(entity)
            entity.invariants
            entity.initializeListeners
            context.transactional(context.transient) {
                initializeLazyFlags(entity)
            }
            context.liveCache.toCache(entity)
        }

    private def initializeLazyFlags(entity: net.fwbrasil.activate.entity.Entity): Unit = {
        val metadata = EntityHelper.getEntityMetadata(entity.getClass)
        val lazyFlags = metadata.propertiesMetadata.filter(p => p.isLazyFlag && p.isTransient)
        for (propertyMetadata <- lazyFlags) {
            val ref = new Var(propertyMetadata, entity, true)
            propertyMetadata.varField.set(entity, ref)
        }
    }

}