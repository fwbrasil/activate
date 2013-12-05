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
import net.fwbrasil.activate.entity.id.EntityId
import net.fwbrasil.activate.entity.id.EntityIdContext
import scala.concurrent.duration.Duration
import net.fwbrasil.activate.entity.map.EntityMapContext

trait BaseEntity extends Serializable with EntityValidation with EntityListeners with EntityId {

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
                ctx.select(manifestClass(references.head.entityMetadata.entityClass)).where { entity: BaseEntity =>
                    import ctx._
                    var criteria: Criteria = (entity.varNamed(references.head.name).get :== this)
                    for (reference <- references.tail)
                        criteria = criteria :|| (entity.varNamed(reference.name).get :== this)
                    criteria
                }.filter(!_.isDeleted)
        }

    private[activate] def deleteWithoutInitilize = {
        baseVar.destroyWithoutInitilize
        for (ref <- vars; if (ref != _baseVar))
            ref.destroyWithoutInitilize
    }

    private var persistedflag = false
    @volatile private var initialized = false
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

    private[activate] def initialize(forWrite: Boolean) = {
        if (!initialized)
            this.synchronized {
                if (!initializing && !initialized) {
                    beforeInitialize
                    initializing = true
                    context.liveCache.loadFromDatabase(this)
                    initializing = false
                    initialized = true
                    afterInitialize
                }
            }
        if (!initializing &&
            forWrite &&
            OptimisticOfflineLocking.isEnabled &&
            isPersisted) {
            val versionVar = _varsMap.get(OptimisticOfflineLocking.versionVarName).asInstanceOf[Var[Long]]
            if (!versionVar.isDirty)
                versionVar.putValueWithoutInitialize(versionVar.getValueWithoutInitialize + 1l)
        }
    }

    def reloadFromDatabase =
        this.synchronized {
            initialized = false
            context.liveCache.loadFromDatabase(this)
            initialized = true
            this
        }

    private[activate] def initializeGraph: Unit =
        initializeGraph(Set())

    private[activate] def initializeGraph(seen: Set[BaseEntity]): Unit =
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
        vars.filterByType[BaseEntity, Var[BaseEntity]]((ref: Var[Any]) => ref.valueClass)

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

    var lastVersionValidation = System.currentTimeMillis

    protected def deferFor(duration: Duration) =
        BaseEntity.deferReadValidationFor(duration, this)

    def shouldValidateRead: Boolean =
        context.shouldValidateRead(this)

    private[activate] def addToLiveCache =
        context.liveCache.toCache(this)

    protected def toStringVars =
        vars

    override def toString =
        EntityHelper.getEntityName(this.getClass) + (
            if (BaseEntity.toStringSeen(this))
                "(loop id->" + id + ")"
            else {
                val varsString =
                    if (initialized) {
                        context.transactional {
                            "(" + toStringVars.mkString(", ") + ")"
                        }
                    } else
                        "(uninitialized id->" + id + ")"
                BaseEntity.toStringRemoveSeen(this)
                varsString
            })

    protected def writeReplace(): AnyRef =
        if (BaseEntity.serializeUsingEvelope)
            new EntitySerializationEnvelopeV2(this)
        else
            this

}

object BaseEntity {

    private[activate] def deferReadValidationFor(duration: Duration, entity: BaseEntity) =
        if (duration.isFinite)
            !(entity.lastVersionValidation + duration.toMillis < System.currentTimeMillis)
        else
            false

    var serializeUsingEvelope = true
    @transient
    private var _toStringLoopSeen: ThreadLocal[MutableHashSet[BaseEntity]] = _
    private def toStringLoopSeen =
        synchronized {
            if (_toStringLoopSeen == null)
                _toStringLoopSeen = new ThreadLocal[MutableHashSet[BaseEntity]]() {
                    override def initialValue = MutableHashSet[BaseEntity]()
                }
            _toStringLoopSeen
        }
    def toStringSeen(entity: BaseEntity) = {
        val set = toStringLoopSeen.get
        val ret = set.contains(entity)
        set += entity
        ret
    }
    def toStringRemoveSeen(entity: BaseEntity) =
        toStringLoopSeen.get -= entity

}

case class CannotDeleteEntity(references: Map[EntityMetadata, List[BaseEntity]])
    extends Exception(s"Can't delete entity due references from ${references.keys.map(_.name)}.")

class EntitySerializationEnvelope[E <: BaseEntity](entity: E) extends Serializable {
    val id = entity.id.asInstanceOf[String]
    val context = entity.context
    protected def readResolve(): Any =
        context.liveCache.materializeEntity(id)
}

class EntitySerializationEnvelopeV2[E <: BaseEntity](entity: E) extends Serializable {
    val id = entity.id.asInstanceOf[AnyRef]
    val entityClass = entity.getClass.asInstanceOf[Class[BaseEntity]]
    val context = entity.context
    protected def readResolve(): Any =
        context.liveCache.materializeEntity(id.asInstanceOf[BaseEntity#ID], entityClass)
}

