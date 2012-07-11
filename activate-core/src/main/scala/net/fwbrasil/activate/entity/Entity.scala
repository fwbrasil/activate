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

trait Entity extends Serializable with ValidEntity {

	def delete =
		if (!isDeleted) {
			initialize
			for (ref <- vars)
				ref.destroy
		}

	@transient
	private var _baseVar: Var[Any] = null

	def baseVar = {
		if (_baseVar == null)
			_baseVar = vars.head
		_baseVar
	}

	def isDeleted =
		baseVar.isDestroyed

	private[activate] def isDeletedSnapshot =
		baseVar.isDestroyedSnapshot

	def isDirty =
		vars.find(_.isDirty).isDefined

	val id: String = null

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

	private[activate] def setInitialized =
		initialized = true

	private[activate] def isInitialized =
		initialized

	// Cylic initializing
	private[activate] def initialize =
		if (!initialized && id != null) // Performance!
			this.synchronized {
				if (!initializing && !initialized && id != null) {
					initializing = true
					context.initialize(this)
					initialized = true
					initializing = false
				}
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

	private[this] def varsOfTypeEntity =
		vars.filterByType[Entity, Var[Entity]]((ref: Var[Any]) => ref.valueClass)

	private[activate] def isInLiveCache =
		context.liveCache.contains(this)

	private[this] def entityMetadata =
		EntityHelper.getEntityMetadata(this.niceClass)

	private[this] def varFields =
		entityMetadata.varFields

	@transient
	private[this] var varFieldsMapCache: MutableMap[String, Var[Any]] = null

	private[this] def buildVarFieldsMap = {
		val res = MutableMap[String, Var[Any]]()
		for (varField <- varFields; ref = varField.get(this).asInstanceOf[Var[Any]]) {
			if (ref.name == null)
				throw new IllegalStateException("Ref should have a name! (" + varField.getName() + ")")
			else
				res.put(ref.name, ref)
		}
		res
	}

	private[this] def varFieldsMap = {
		if (varFieldsMapCache == null) {
			varFieldsMapCache = buildVarFieldsMap
		}
		varFieldsMapCache
	}

	private[activate] def vars =
		varFieldsMap.values

	private[activate] def context: ActivateContext =
		ActivateContext.contextFor(this.niceClass)

	private[fwbrasil] def varNamed(name: String) =
		varFieldsMap.get(name)

	private[activate] def addToLiveCache =
		context.liveCache.toCache(this)

	protected def toStringVars =
		vars

	override def toString =
		EntityHelper.getEntityName(this.niceClass) + (
			try {
				if (Entity.toStringSeen(this))
					"(loop id->" + id + ")"
				else context.transactional {
					"(" + toStringVars.mkString(", ") + ")"
				}
				//				else
				//					"(uninitialized id->" + id + ")"
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
	private[this] var _toStringLoopSeen: ThreadLocal[MutableHashSet[Entity]] = _
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
	type EntityName = net.fwbrasil.activate.entity.EntityName
	type Var[A] = net.fwbrasil.activate.entity.Var[A]

	private[activate] val liveCache: LiveCache
	private[activate] def initialize[E <: Entity](entity: E)

}