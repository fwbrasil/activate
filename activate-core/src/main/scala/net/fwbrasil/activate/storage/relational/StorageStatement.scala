package net.fwbrasil.activate.storage.relational

import language.existentials
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.StorageValue

abstract class StorageStatement

abstract class DmlStorageStatement(
    val entityClass: Class[_],
    val entityId: BaseEntity#ID,
    val propertyMap: Map[String, StorageValue])
        extends StorageStatement {

    private def entityName = EntityHelper.getEntityName(entityClass)

    override def toString =
        this.getClass.getSimpleName +
            "(" + entityName + ", " + entityId + ", " + propertyMap + ")"
}

case class InsertStorageStatement(
    override val entityClass: Class[_],
    override val entityId: BaseEntity#ID,
    override val propertyMap: Map[String, StorageValue])
        extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class UpdateStorageStatement(
    override val entityClass: Class[_],
    override val entityId: BaseEntity#ID,
    override val propertyMap: Map[String, StorageValue])
        extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class DeleteStorageStatement(
    override val entityClass: Class[_],
    override val entityId: BaseEntity#ID,
    override val propertyMap: Map[String, StorageValue])
        extends DmlStorageStatement(entityClass, entityId, propertyMap)

case class QueryStorageStatement(query: Query[_], entitiesReadFromCache: List[List[BaseEntity]])
    extends StorageStatement

case class ModifyStorageStatement(statement: MassModificationStatement)
    extends StorageStatement

case class DdlStorageStatement(action: ModifyStorageAction)
    extends StorageStatement
