package net.fwbrasil.activate.entity

import net.fwbrasil.radon.transaction.TransactionContext
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.cache.live.LiveCache
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

trait Entity extends Serializable with EntityValidation {

    @transient
    private var _baseVar: Var[Any] = null

    private[activate] var _varsMap: JHashMap[String, Var[Any]] = null

    @transient
    private var _vars: List[Var[Any]] = null

    final var version: Long = 0l

    private[activate] def varsMap =
        _varsMap

    private[activate] def buildVarsMap = {
        // Implementation injected by EntityEnhance
    }

    private[activate] def putVar(name: String, ref: Var[Any]) =
        _varsMap.put(name, ref)

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

    protected def postConstruct = {
        buildVarsMap
        validateOnCreate
        addToLiveCache
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

    def delete =
        if (!isDeleted) {
            initialize(forWrite = true)
            _baseVar.destroy
            for (ref <- vars; if (ref != _baseVar))
                ref.destroy
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

    // Cyclic initializing
    private[activate] def initialize(forWrite: Boolean) = {
        this.synchronized {
            if (!initialized && !initializing && id != null) {
                initializing = true
                context.liveCache.loadFromDatabase(this, withinTransaction = false)
                initialized = true
                initializing = false
                postInitialize
            }
        }
        if (!initializing && (forWrite || OptimisticOfflineLocking.validateReads) &&
            OptimisticOfflineLocking.isEnabled && isPersisted) {
            val versionVar = _varsMap.get(OptimisticOfflineLocking.versionVarName).asInstanceOf[Var[Long]]
            if (!versionVar.isDirty)
                versionVar.putValueWithoutInitialize(versionVar.getValueWithoutInitialize + 1l)
        }
    }

    protected def postInitialize = {}

    private[activate] def uninitialize =
        this.synchronized {
            context.liveCache.loadFromDatabase(this, withinTransaction = true)
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
    type Alias = net.fwbrasil.activate.entity.Alias
    type Var[A] = net.fwbrasil.activate.entity.Var[A]

    protected[activate] val liveCache = new LiveCache(this)

    protected[activate] def entityMaterialized(entity: Entity) = {}

}