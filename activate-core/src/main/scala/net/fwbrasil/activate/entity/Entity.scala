package net.fwbrasil.activate.entity

import net.fwbrasil.radon.transaction.TransactionContext
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.Reflection.toRichClass
import net.fwbrasil.activate.util.RichList._
import java.lang.reflect.Field
import java.lang.reflect.Method
import scala.collection.mutable.{ Map => MutableMap, HashSet => MutableHashSet }
import java.util.Date
import org.joda.time.DateTime
import java.util.{ HashMap => JHashMap }

trait Entity extends Serializable with EntityValidation {

	@transient
	private var _baseVar: Var[Any] = null

	private[activate] var _varsMap: JHashMap[String, Var[Any]] = null

	@transient
	private var _vars: List[Var[Any]] = null

	private def varsMap = {
		//		if (_varsMap == null) {
		//			_varsMap = new JHashMap[String, Var[Any]]()
		//			buildVarsMap
		//		}
		_varsMap
	}

	private[activate] def buildVarsMap = {
		// Implementation injected by EntityEnhance
	}

	private[activate] def putVar(name: String, ref: Var[Any]) =
		_varsMap.put(name, ref)

	private[activate] def vars = {
		if (_vars == null) {
			import scala.collection.JavaConversions._
			_vars = varsMap.values.toList
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
		vars.find(_.isDirty).isDefined

	val id: String = null

	def delete =
		if (!isDeleted) {
			initialize
			_baseVar.destroy
			for (ref <- vars; if (ref != _baseVar))
				ref.destroy
		}

	def creationTimestamp = UUIDUtil timestamp id.substring(0, 35)
	def creationDate = new Date(creationTimestamp)
	def creationDateTime = new DateTime(creationTimestamp)

	private var persistedflag = false
	private var initialized = true
	private var initializing = false

	private[activate] def setPersisted =
		persistedflag = true

	private[activate] def setNotPersisted =
		persistedflag = false

	private[activate] def isPersisted =
		persistedflag

	private[activate] def setNotInitialized =
		initialized = false

	private[activate] def setInitialized = {
		initializing = false
		initialized = true
	}

	private[activate] def isInitialized =
		initialized

	// Cyclic initializing
	private[activate] def initialize =
		this.synchronized {
			if (!initialized && !initializing && id != null) {
				initializing = true
				context.initialize(this)
				initialized = true
				initializing = false
			}
		}

	private[activate] def uninitialize =
		this.synchronized {
			initialized = false
		}

	private[activate] def initializeGraph: Unit =
		initializeGraph(Set())

	private[activate] def initializeGraph(seen: Set[Entity]): Unit =
		this.synchronized {
			initialize
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

	private def entityMetadata =
		EntityHelper.getEntityMetadata(this.niceClass)

	private def varFields =
		entityMetadata.varFields

	private[activate] def context: ActivateContext =
		ActivateContext.contextFor(this.niceClass)

	private[fwbrasil] def varNamed(name: String) =
		varsMap.get(name)

	private[activate] def addToLiveCache =
		context.liveCache.toCache(this)

	protected def toStringVars =
		vars

	override def toString =
		EntityHelper.getEntityName(this.niceClass) + (
			try {
				if (Entity.toStringSeen(this))
					"(loop id->" + id + ")"
				else if (initialized)
					context.transactional {
						"(" + toStringVars.mkString(", ") + ")"
					}
				else
					"(uninitialized id->" + id + ")"
			} finally { Entity.toStringRemoveSeen(this) })

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
	def toStringRemoveSeen(entity: Entity) =
		toStringLoopSeen.get -= entity
}

class EntitySerializationEnvelope[E <: Entity](entity: E) extends Serializable {
	val id = entity.id
	val context = entity.context
	protected def readResolve(): Any =
		context.liveCache.materializeEntity(id)
}

trait EntityContext extends ValueContext with TransactionContext {

	type Entity = net.fwbrasil.activate.entity.Entity
	type Alias = net.fwbrasil.activate.entity.Alias
	type Var[A] = net.fwbrasil.activate.entity.Var[A]

	private[activate] val liveCache: LiveCache
	private[activate] def initialize[E <: Entity](entity: E)

}