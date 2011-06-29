package net.fwbrasil.activate.storage.relational

import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.query._

abstract sealed case class StorageStatement

abstract sealed case class DmlStorageStatement(entityClass: Class[_], entityId: String, propertyMap: Map[String, StorageValue])
	extends StorageStatement

case class InsertDmlStorageStatement(override val entityClass: Class[_], override val entityId: String, override val propertyMap: Map[String, StorageValue])
	extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class UpdateDmlStorageStatement(override val entityClass: Class[_], override val entityId: String, override val propertyMap: Map[String, StorageValue])
	extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class DeleteDmlStorageStatement(override val entityClass: Class[_], override val entityId: String, override val propertyMap: Map[String, StorageValue])
	extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class QueryStorageStatement(query: Query[_])
	extends StorageStatement

