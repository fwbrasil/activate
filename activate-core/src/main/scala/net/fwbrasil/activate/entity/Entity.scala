package net.fwbrasil.activate.entity

import java.lang.reflect.{ Modifier, Field, Method }
import net.fwbrasil.activate.cache.live._
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionContext
import scala.collection._

trait Entity extends Serializable with ValidEntity {

	def delete =
		if (!isDeleted) {
			initialize
			for (ref <- vars)
				ref.destroy
		}

	def isDeleted =
		vars.find(_.name != "id").get.isDestroyed

	def isDirty =
		vars.filter(_.name != "id").find(_.isDirty).isDefined

	val id = {
		val uuid = UUIDUtil.generateUUID
		val classId = EntityHelper.getEntityClassHashId(this.getClass)
		uuid + "-" + classId
	}

	private[this] var persistedflag = false
	private[this] var initialized = true

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
			if (!initialized && id != null)
				context.initialize(this.asInstanceOf[Entity])
			initialized = true
		}

	private[activate] def initializeGraph: Unit =
		initializeGraph(Set())

	private[activate] def initializeGraph(seen: Set[Entity]): Unit =
		this.synchronized {
			initialize
			for (ref <- varsOfTypeEntity)
				if (ref.get.nonEmpty) {
					val entity = ref.get.get.asInstanceOf[Entity]
					if (!seen.contains(entity))
						entity.initializeGraph(seen + this)
				}
		}

	private[this] def varsOfTypeEntity =
		vars.filter((ref: Var[_]) => classOf[Entity].isAssignableFrom(ref.valueClass))

	private[activate] def isInLiveCache =
		context.liveCache.contains(this.asInstanceOf[Entity])

	private[this] def entityMetadata =
		EntityHelper.getEntityMetadata(this.getClass)

	private[this] def varFields =
		entityMetadata.varFields

	//	private[activate] def idField =
	//		entityMetadata.idField

	@transient
	private[this] var varFieldsMapCache: Map[String, Var[_]] = _

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

	private[activate] def context: ActivateContext = {
		ActivateContext.contextFor(this.getClass.asInstanceOf[Class[Entity]])
	}

	private[activate] def varNamed(name: String) =
		varFieldsMap.get(name)

	private[activate] def addToLiveCache =
		context.liveCache.toCache(this.asInstanceOf[Entity])

	private[activate] def cachedInstance =
		context.liveCache.cachedInstance(this.asInstanceOf[Entity])

	override def toString =
		this.getClass.getSimpleName + "(" + vars.mkString(", ") + ")"

}

class EntityPropertyMetadata(
		val varField: Field,
		entityMethods: List[Method],
		entityClass: Class[Entity],
		varTypes: java.util.HashMap[String, Class[_]]) {
	val name = varField.getName
	val propertyType =
		varTypes.get(name)
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
	val varTypes =
		Reflection.getStatic(entityClass, "varTypes").asInstanceOf[java.util.HashMap[String, Class[_]]]
	val propertiesMetadata =
		for (varField <- varFields; if (isEntityProperty(varField)))
			yield new EntityPropertyMetadata(varField, allMethods, entityClass, varTypes)
	allMethods.foreach(_.setAccessible(true))
	allFields.foreach(_.setAccessible(true))
	override def toString = "Entity metadata for " + name
}

object EntityHelper {

	private[this] val entitiesMetadatas =
		mutable.HashMap[String, EntityMetadata]()

	private[this] val concreteEntityClasses =
		mutable.HashMap[Class[Entity], List[Class[Entity]]]()

	var initialized = false

	def concreteClasses(clazz: Class[Entity]) =
		concreteEntityClasses.getOrElse(clazz, {
			for ((hash, metadata) <- entitiesMetadatas; if (clazz == metadata.entityClass || clazz.isAssignableFrom(metadata.entityClass)))
				yield metadata.entityClass
		})

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
		entitiesMetadatas(entityId.split("-").last).entityClass

	def getEntityClassHashId(entityClass: Class[_]): String =
		getEntityClassHashId(getEntityName(entityClass))

	def getEntityName(entityClass: Class[_]) = {
		val annotation = entityClass.getAnnotation(classOf[EntityName])
		if (annotation != null)
			annotation.value
		else
			entityClass.getSimpleName.split('$')(0)
	}

	def getEntityClassHashId(entityName: String): String =
		Integer.toHexString(entityName.hashCode)

	def getEntityMetadata(clazz: Class[_]) =
		entitiesMetadatas(getEntityClassHashId(clazz))

}

trait EntityContext extends ValueContext with TransactionContext with ValidEntityContext {

	private[activate] val liveCache: LiveCache

	type Entity = net.fwbrasil.activate.entity.Entity
	type Entityname = net.fwbrasil.activate.entity.EntityName

	type Var[A] = net.fwbrasil.activate.entity.Var[A]

	def initialize(entity: Entity)

}