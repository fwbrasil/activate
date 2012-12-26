package net.fwbrasil.activate.storage.relational

import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.statement.query._
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.statement.mass.MassModificationStatement

abstract class StorageStatement

abstract class DmlStorageStatement(val entityClass: Class[_], val entityId: String, val propertyMap: Map[String, StorageValue])
		extends StorageStatement {
	override def toString = this.getClass.getSimpleName + "(" + EntityHelper.getEntityName(entityClass) + ", " + entityId + ", " + propertyMap + ")"
}

import language.existentials

case class InsertDmlStorageStatement(override val entityClass: Class[_], override val entityId: String, override val propertyMap: Map[String, StorageValue])
	extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class UpdateDmlStorageStatement(override val entityClass: Class[_], override val entityId: String, override val propertyMap: Map[String, StorageValue])
	extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class DeleteDmlStorageStatement(override val entityClass: Class[_], override val entityId: String, override val propertyMap: Map[String, StorageValue])
	extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class QueryStorageStatement(query: Query[_])
	extends StorageStatement

case class ModifyStorageStatement(statement: MassModificationStatement)
	extends StorageStatement

case class DdlStorageStatement(val action: ModifyStorageAction)
	extends StorageStatement
