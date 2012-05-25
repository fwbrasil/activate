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

trait Entity extends Serializable {

	implicit val implicitEntity = this: this.type

	def delete =
		if (!isDeleted) {
			initialize
			for (ref <- vars)
				ref.destroy
		}

	def isDeleted =
		vars.head.isDestroyed

	def isDirty =
		vars.find(_.isDirty).isDefined

	private[activate] def isDeletedSnapshot =
		vars.head.isDestroyedSnapshot

	val id: String = null

	private var persistedflag = false
	private var initialized = true
	private var initializing = false

	private[activate] def setPersisted =
		persistedflag = true

	private[activate] def isPersisted =
		persistedflag

	private[activate] def setNotInitialized =
		initialized = false

	private[activate] def setInitialized =
		initialized = true

	private[activate] def isInitialized =
		initialized

	private[activate] def initialize =
		if (!initializing && !initialized && id != null) // Performance!
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
	private[this] var varFieldsMapCache: MutableMap[String, Var[Any]] = _

	private[this] def buildVarFieldsMap = {
		val res = MutableMap[String, Var[Any]]()
		for (varField <- varFields; ref = varField.get(this).asInstanceOf[Var[Any]]; if (ref != null))
			yield if (ref.name == null)
			throw new IllegalStateException("Ref should have a name! (" + varField.getName() + ")")
		else
			res.put(ref.name, ref)
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
		_context

	private[this] lazy val _context =
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

class EntityPropertyMetadata(
		val entityMetadata: EntityMetadata,
		val varField: Field,
		entityMethods: List[Method],
		entityClass: Class[Entity],
		varTypes: Map[String, Class[_]]) {
	val javaName = varField.getName
	val name = javaName.split('$').last
	val propertyType =
		varTypes.getOrElse(name, null)
	require(propertyType != null)
	if (propertyType == classOf[Enumeration#Value])
		throw new IllegalArgumentException("To use enumerations with activate you must sublcass Val. " +
			"Instead of \"type MyEnum = Value\", use " +
			"\"case class MyEnum(name: String) extends Val(name)\"")
	val getter = entityMethods.find(_.getName == javaName).get
	val setter = entityMethods.find(_.getName == javaName + "_$eq").getOrElse(null)
	val isMutable =
		setter != null
	val tval =
		EntityValue.tvalFunctionOption[Any](propertyType)
			.getOrElse(throw new IllegalStateException("Invalid entity property type. " + entityMetadata.name + "." + name + ": " + propertyType))
	varField.setAccessible(true)
	getter.setAccessible(true)
	override def toString = "Property: " + name
}

class EntityMetadata(
		val name: String,
		val entityClass: Class[Entity]) {
	val allFields =
		Reflection.getDeclaredFieldsIncludingSuperClasses(entityClass)
	val allMethods =
		Reflection.getDeclaredMethodsIncludingSuperClasses(entityClass)
	val varFields =
		allFields.filter((field: Field) => classOf[Var[_]] == field.getType)
	val idField =
		allFields.find(_.getName == "id").get
	def isEntityProperty(varField: Field) =
		varField.getName != "id" && allMethods.find(_.getName == varField.getName).nonEmpty
	val varTypes = {
		import scala.collection.JavaConversions._
		var clazz: Class[_] = entityClass
		var ret = Map[String, Class[_]]()
		do {
			val map = Reflection.getStatic(clazz, "varTypes").asInstanceOf[java.util.HashMap[String, Class[_]]]
			if (map != null)
				ret ++= map
			clazz = clazz.getSuperclass
		} while (clazz != null)
		ret
	}
	val propertiesMetadata =
		(for (varField <- varFields; if (isEntityProperty(varField)))
			yield new EntityPropertyMetadata(this, varField, allMethods, entityClass, varTypes)).sortBy(_.name)
	allMethods.foreach(_.setAccessible(true))
	allFields.foreach(_.setAccessible(true))
	override def toString = "Entity metadata for " + name
}

object EntityHelper {

	private[this] val entitiesMetadatas =
		MutableMap[String, EntityMetadata]()

	private[this] val concreteEntityClasses =
		MutableMap[Class[_ <: Entity], List[Class[Entity]]]()

	var initialized = false

	def metadatas =
		entitiesMetadatas.values.toList.sortBy(_.name)

	def concreteClasses[E <: Entity](clazz: Class[E]) =
		concreteEntityClasses.getOrElse(clazz, {
			for (
				(hash, metadata) <- entitiesMetadatas;
				if ((clazz == metadata.entityClass || clazz.isAssignableFrom(metadata.entityClass)) && metadata.entityClass.isConcreteClass)
			) yield metadata.entityClass
		}).toList.asInstanceOf[List[Class[_ <: E]]]

	def initialize(referenceClass: Class[_]) = synchronized {
		if (!initialized) {
			UUIDUtil.generateUUID
			for (entityClass <- EntityEnhancer.enhancedEntityClasses(referenceClass); if (!entityClass.isInterface()))
				if (!entityClass.isInterface()) {
					val entityClassHashId = getEntityClassHashId(entityClass)
					if (entitiesMetadatas.contains(entityClassHashId))
						throw new IllegalStateException("Duplicate entity name.")
					val entityName = getEntityName(entityClass)
					entitiesMetadatas += (entityClassHashId -> new EntityMetadata(entityName, entityClass))
				}
		}
		initialized = true
	}

	def getEntityClassFromId(entityId: String) =
		entitiesMetadatas(entityId.substring(37)).entityClass

	def getEntityClassHashId(entityClass: Class[_]): String =
		getEntityClassHashId(getEntityName(entityClass))

	def getEntityName(entityClass: Class[_]) = {
		val annotation = entityClass.getAnnotation(classOf[EntityName])
		if (annotation != null)
			annotation.value
		else {
			entityClass.getSimpleName
		}
	}

	def getEntityClassHashId(entityName: String): String =
		Integer.toHexString(entityName.hashCode)

	def getEntityMetadata(clazz: Class[_]) =
		entitiesMetadatas(getEntityClassHashId(clazz))

}

trait EntityContext extends ValueContext with TransactionContext {

	type Entity = net.fwbrasil.activate.entity.Entity
	type EntityName = net.fwbrasil.activate.entity.EntityName
	type Var[A] = net.fwbrasil.activate.entity.Var[A]

	private[activate] val liveCache: LiveCache
	private[activate] def initialize[E <: Entity](entity: E)

}