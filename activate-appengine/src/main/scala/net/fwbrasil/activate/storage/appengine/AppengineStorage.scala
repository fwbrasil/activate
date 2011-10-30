package net.fwbrasil.activate.storage.appengine

import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.query._
import net.fwbrasil.activate.storage._
import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.entity.EntityHelper._
import com.google.appengine.api.datastore.{ Entity => DatastoreEntity, DatastoreServiceFactory, KeyFactory }
import com.google.appengine.api.datastore.{ Query => DatastoreQuery }

class AppengineStorage extends MarshalStorage {

	val datastore = DatastoreServiceFactory.getDatastoreService()

	override def store(insertMap: Map[Entity, Map[String, StorageValue]], updateMap: Map[Entity, Map[String, StorageValue]], deleteMap: Map[Entity, Map[String, StorageValue]]): Unit = {
		val transaction = datastore.beginTransaction
		try {
			for ((entity, propertyMap) <- insertMap) {
				val entityName = getEntityName(entity.getClass)
				val datastoreEntity = new DatastoreEntity(entityName, entity.id)
				setProperties(datastoreEntity, propertyMap)
				datastore.put(datastoreEntity)
			}
			for ((entity, propertyMap) <- updateMap) {
				val datastoreEntity = getDatastoreEntity(entity)
				setProperties(datastoreEntity, propertyMap)
				datastore.put(datastoreEntity)
			}
			for ((entity, propertyMap) <- deleteMap) {
				datastore.delete(getKey(entity))
			}
		} catch {
			case exception =>
				transaction.rollback
				throw exception
		}
	}

	private[this] def setProperties(datastoreEntity: DatastoreEntity, propertyMap: Map[String, StorageValue]) =
		for ((name, storageValue) <- propertyMap)
			datastoreEntity.setProperty(name, toDatastoreValue(storageValue))

	private[this] def toDatastoreValue(storageValue: StorageValue) =
		storageValue.value.getOrElse(null)

	private[this] def getKey(entity: Entity) = {
		val entityName = getEntityName(entity.getClass)
		KeyFactory.createKey(entityName, entity.id)
	}

	private[this] def getDatastoreEntity(entity: Entity) =
		datastore.get(getKey(entity))

	def query(query: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
		if(query.from.entitySources.size > 1)
			throw new UnsupportedOperationException("AppengineStorage doesn't supports query with joins between entities.")
		val entityClass = query.from.entitySources.head.entityClass
		val entityName = getEntityName(entityClass)
		val datastoreQuery = new DatastoreQuery(entityName)
		null
	}
}