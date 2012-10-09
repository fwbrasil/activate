package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.uuid.UUIDUtil
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.util.Reflection.toRichClass

object EntityHelper {

	private[this] val entitiesMetadatas =
		MutableMap[String, EntityMetadata]()

	private[this] val concreteEntityClasses =
		MutableMap[Class[_ <: Entity], List[Class[Entity]]]()

	def metadatas =
		entitiesMetadatas.values.toList.sortBy(_.name)

	def allConcreteEntityClasses =
		concreteEntityClasses.values.flatten.toSet

	def concreteClasses[E <: Entity](clazz: Class[E]) =
		concreteEntityClasses.getOrElse(clazz, {
			for (
				(hash, metadata) <- entitiesMetadatas;
				if ((clazz == metadata.entityClass || clazz.isAssignableFrom(metadata.entityClass)) && metadata.entityClass.isConcreteClass)
			) yield metadata.entityClass
		}).toList.asInstanceOf[List[Class[_ <: E]]]

	def initialize(referenceClass: Class[_]) = synchronized {
		UUIDUtil.generateUUID
		for (entityClass <- EntityEnhancer.enhancedEntityClasses(referenceClass))
			if (!entityClass.isInterface()) {
				val entityClassHashId = getEntityClassHashId(entityClass)
				if (entitiesMetadatas.contains(entityClassHashId))
					throw new IllegalStateException("Duplicate entity name.")
				val entityName = getEntityName(entityClass)
				entitiesMetadatas += (entityClassHashId -> new EntityMetadata(entityName, entityClass))
			}
	}

	def getEntityClassFromIdOption(entityId: String) =
		if (entityId.length >= 35)
			entitiesMetadatas.get(normalizeHex(entityId.substring(37))).map(_.entityClass)
		else
			None

	def getEntityClassFromId(entityId: String) =
		getEntityClassFromIdOption(entityId).get

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

	private def normalizeHex(hex: String) =
		if (hex.length == 8)
			hex
		else
			hex + (for (i <- 0 until (8 - hex.length)) yield "0").mkString("")

	def getEntityClassHashId(entityName: String): String =
		normalizeHex(Integer.toHexString(entityName.hashCode).take(8))

	def getEntityMetadataOption(clazz: Class[_]) =
		entitiesMetadatas.get(getEntityClassHashId(clazz))

	def getEntityMetadata(clazz: Class[_]) =
		entitiesMetadatas(getEntityClassHashId(clazz))

}
