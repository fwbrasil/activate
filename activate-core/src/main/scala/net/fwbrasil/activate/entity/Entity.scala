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
import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }

trait Entity extends Serializable with ValidEntity {

	def delete =
		if (!isDeleted) {
			initialize
			for (ref <- vars)
				ref.destroy
		}

	def isDeleted =
		vars.find(_.name != "id").get.isDestroyed

	private[activate] def isDeletedSnapshot =
		vars.find(_.name != "id").get.isDestroyedSnapshot

	private def isDirty =
		vars.filter(_.name != "id").find(_.isDirty).isDefined

	val id = {
		val uuid = UUIDUtil.generateUUID
		val classId = EntityHelper.getEntityClassHashId(this.niceClass)
		uuid + "-" + classId
	}

	private[this] var persistedflag = false
	private[this] var initialized = true
	private[this] var initializing = false

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
	private[this] var varFieldsMapCache: Map[String, Var[Any]] = _

	private[this] def buildVarFieldsMap =
		(for (varField <- varFields; ref = varField.get(this).asInstanceOf[Var[Any]]; if (ref != null))
			yield if (ref.name == null)
			throw new IllegalStateException("Ref should have a name! (" + varField.getName() + ")")
		else
			(ref.name -> ref)).toMap

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

	private[this] lazy val _context = {
		ActivateContext.contextFor(this.niceClass)
	}

	private[activate] def varNamed(name: String) =
		varFieldsMap.get(name)

	private[activate] def addToLiveCache =
		context.liveCache.toCache(this)

	private[activate] def cachedInstance =
		context.liveCache.cachedInstance(this)

	override def toString =
		EntityHelper.getEntityName(this.niceClass) + (
			try {
				if (Entity.toStringSeen(this))
					"(loop id->" + id + ")"
				else if (initialized)
					"(" + vars.mkString(", ") + ")"
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
	private[this] val _toStringLoopSeen = new ThreadLocal[MutableHashSet[Entity]]() {
		override def initialValue = MutableHashSet[Entity]()
	}
	def toStringSeen(entity: Entity) = {
		val set = _toStringLoopSeen.get
		val ret = set.contains(entity)
		set += entity
		ret
	}
	def toStringRemoveSeen(entity: Entity) =
		_toStringLoopSeen.get -= entity
}

class EntitySerializationEnvelope[E <: Entity](entity: E) extends Serializable {
	val id = entity.id
	val context = entity.context
	protected def readResolve(): Any =
		context.liveCache.materializeEntity(id)
}

class EntityPropertyMetadata(
		val varField: Field,
		entityMethods: List[Method],
		entityClass: Class[Entity],
		varTypes: Map[String, Class[_]]) {
	val name = varField.getName
	val propertyType =
		varTypes.getOrElse(name, null)
	require(propertyType != null)
	if (propertyType == classOf[Enumeration#Value])
		throw new IllegalArgumentException("To use enumerations with activate you must sublcass Val. " +
			"Instead of \"type MyEnum = Value\", use " +
			"\"case class MyEnum(name: String) extends Val(name)\"")
	val getter = entityMethods.find(_.getName == name).get
	val setter = entityMethods.find(_.getName == name + "_$eq").getOrElse(null)
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
	val invariantMethods =
		allMethods.filter((method: Method) => method.getReturnType == classOf[Invariant] && method.getName != "invariant")
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
		for (varField <- varFields; if (isEntityProperty(varField)))
			yield new EntityPropertyMetadata(varField, allMethods, entityClass, varTypes)
	allMethods.foreach(_.setAccessible(true))
	allFields.foreach(_.setAccessible(true))
	override def toString = "Entity metadata for " + name
}

object EntityHelper {

	private[this] val entitiesMetadatas =
		MutableHashMap[String, EntityMetadata]()

	private[this] val concreteEntityClasses =
		MutableHashMap[Class[_ <: Entity], List[Class[Entity]]]()

	var initialized = false

	def concreteClasses[E <: Entity](clazz: Class[E]) =
		concreteEntityClasses.getOrElse(clazz, {
			for (
				(hash, metadata) <- entitiesMetadatas;
				if ((clazz == metadata.entityClass || clazz.isAssignableFrom(metadata.entityClass)) && metadata.entityClass.isConcreteClass)
			) yield metadata.entityClass
		}).toList.asInstanceOf[List[Class[_ <: E]]]

	def initialize = synchronized {
		if (!initialized) {
			for (entityClass <- EntityEnhancer.enhancedEntityClasses; if (!entityClass.isInterface()))
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

trait EntityContext extends ValueContext with TransactionContext with ValidEntityContext {

	type Entity = net.fwbrasil.activate.entity.Entity
	type EntityName = net.fwbrasil.activate.entity.EntityName
	type Var[A] = net.fwbrasil.activate.entity.Var[A]

	private[activate] val liveCache: LiveCache
	private[activate] def initialize[E <: Entity](entity: E)

}