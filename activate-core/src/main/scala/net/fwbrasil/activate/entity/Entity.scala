package net.fwbrasil.activate.entity

import scala.tools.nsc.util.ClassPath.DefaultJavaContext
import java.lang.reflect.{ Modifier, Field, Method }
import net.fwbrasil.activate.cache.live._
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionContext
import scala.collection._
import tools.scalap.scalax.rules.scalasig.ClassSymbol

trait Entity {

	def delete = {
		initialize
		for (ref <- vars)
			ref.destroy
	}

	def isDeleted =
		vars.head.isDestroyed

	val id = {
		val uuid = UUIDUtil.generateUUID
		val classId = EntityHelper.getEntityClassHashId(this.getClass)
		uuid + "-" + classId
	}

	private[this] var persistedflag = false
	private[this] var isVarsBound = false
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

	private[activate] def isInLiveCache =
		context.liveCache.contains(this.asInstanceOf[Entity])

	private[this] def entityMetadata =
		EntityHelper.getEntityMetadata(this.getClass)

	private[this] def varFields =
		entityMetadata.varFields

	private[activate] def idField =
		entityMetadata.idField

	@transient
	private[this] var varFieldsMapCache: Map[String, Var[_]] = _

	private[this] def buildVarFieldsMap =
		(for (varField <- varFields; ref = varField.get(this).asInstanceOf[Var[Any]])
			yield if (ref.name == null)
			throw new IllegalStateException("Ref should have a name!")
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

	def context: ActivateContext = {
		ActivateContext.contextFor(this.getClass)
	}

	private[activate] def varNamed(name: String) =
		varFieldsMap.get(name)

	private[activate] def addToLiveCache =
		context.liveCache.toCache(this.asInstanceOf[Entity])

	private[activate] def cachedInstance =
		context.liveCache.cachedInstance(this.asInstanceOf[Entity])

	override def toString =
		this.getClass.getSimpleName + "(" + id + "@" + hashCode + ")"

}

class EntityPropertyMetadata(
	val varField: Field,
	entityMethods: List[Method],
	scalaSig: ClassSymbol,
	entityClass: Class[Entity]) {
	val name = varField.getName
	val propertyType =
		Reflection.getStatic(entityClass, "varTypes").asInstanceOf[java.util.HashMap[String, Class[_]]].get(name)
	val getter = entityMethods.find(_.getName == name).get
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
		allFields.filter((field: Field) => classOf[Var[_]].isAssignableFrom(field.getType))
	val idField =
		allFields.filter(_.getName.equals("id")).head
	lazy val scalaSig = Reflection.getScalaSig(entityClass)
	def isEntityProperty(varField: Field) =
		allMethods.find(_.getName == varField.getName).nonEmpty
	val propertiesMetadata =
		for (varField <- varFields; if (isEntityProperty(varField)))
			yield new EntityPropertyMetadata(varField, allMethods, scalaSig, entityClass)
	idField.setAccessible(true)
	varFields.foreach(_.setAccessible(true))
	override def toString = "Entity metadata for " + name
}

object EntityHelper {

	private[this] val entitiesMetadatas =
		mutable.HashMap[String, EntityMetadata]()

	var initialized = false

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

	def getEntityName(entityClass: Class[_]) =
		entityClass.getSimpleName.split('$')(0)

	def getEntityClassHashId(entityName: String): String =
		Integer.toHexString(entityName.hashCode)

	def getEntityMetadata(clazz: Class[_]) =
		entitiesMetadatas(getEntityClassHashId(clazz))

}

trait EntityContext extends ValueContext with TransactionContext {

	private[activate] val liveCache: LiveCache

	type Entity = net.fwbrasil.activate.entity.Entity

	type Var[A] = net.fwbrasil.activate.entity.Var[A]

	def initialize(entity: Entity)

	implicit def varToValue[A](ref: Var[A]): A =
		if (ref == null)
			null.asInstanceOf[A]
		else !ref

}