package net.fwbrasil.activate.storage.marshalling

import java.util.{ Date, Calendar }
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.Reflection.get
import net.fwbrasil.activate.util.Reflection.getObject
import net.fwbrasil.activate.util.Reflection.materializeJodaInstant
import org.joda.time.base.AbstractInstant
import net.fwbrasil.activate.statement.StatementEntityValue
import net.fwbrasil.activate.entity.EnumerationEntityValue
import net.fwbrasil.activate.entity.DateEntityValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.entity.FloatEntityValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.CharEntityValue
import net.fwbrasil.activate.entity.DoubleEntityValue
import net.fwbrasil.activate.entity.JodaInstantEntityValue
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.entity.StringEntityValue
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.LongEntityValue
import net.fwbrasil.activate.entity.BooleanEntityValue
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.entity.ByteArrayEntityValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.entity.BigDecimalEntityValue
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.CalendarEntityValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.SerializableEntityValue
import net.fwbrasil.activate.entity.ListEntityValue
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.migration.CreateTable
import net.fwbrasil.activate.migration.RenameTable
import net.fwbrasil.activate.migration.RenameColumn
import net.fwbrasil.activate.migration.RemoveTable
import net.fwbrasil.activate.migration.RemoveColumn
import net.fwbrasil.activate.migration.RemoveIndex
import net.fwbrasil.activate.migration.AddColumn
import net.fwbrasil.activate.migration.AddIndex
import net.fwbrasil.activate.migration.Column
import net.fwbrasil.activate.migration.CustomScriptAction
import net.fwbrasil.activate.migration.AddReference
import net.fwbrasil.activate.migration.RemoveReference
import net.fwbrasil.activate.migration.CreateListTable
import net.fwbrasil.activate.migration.RemoveListTable
import net.fwbrasil.activate.entity.ReferenceListEntityValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.LazyListEntityValue
import net.fwbrasil.activate.entity.LazyListEntityValue
import net.fwbrasil.activate.entity.LazyList
import net.fwbrasil.activate.migration.ModifyColumnType
import net.fwbrasil.activate.entity.EncoderEntityValue

object Marshaller {

    def unmarshalling(storageValue: StorageValue, entityValue: EntityValue[_]): EntityValue[_] =
        (storageValue, entityValue) match {
            case (storageValue: IntStorageValue, entityValue: IntEntityValue) =>
                IntEntityValue(storageValue.value)
            case (storageValue: LongStorageValue, entityValue: LongEntityValue) =>
                LongEntityValue(storageValue.value)
            case (storageValue: BooleanStorageValue, entityValue: BooleanEntityValue) =>
                BooleanEntityValue(storageValue.value)
            case (storageValue: StringStorageValue, entityValue: CharEntityValue) =>
                CharEntityValue(storageValue.value.map(_.charAt(0)))
            case (storageValue: StringStorageValue, entityValue: StringEntityValue) =>
                StringEntityValue(storageValue.value)
            case (storageValue: FloatStorageValue, entityValue: FloatEntityValue) =>
                FloatEntityValue(storageValue.value)
            case (storageValue: DoubleStorageValue, entityValue: DoubleEntityValue) =>
                DoubleEntityValue(storageValue.value)
            case (storageValue: BigDecimalStorageValue, entityValue: BigDecimalEntityValue) =>
                BigDecimalEntityValue(storageValue.value)
            case (storageValue: DateStorageValue, entityValue: DateEntityValue) =>
                DateEntityValue(storageValue.value)
            case (storageValue: DateStorageValue, entityValue: JodaInstantEntityValue[_]) =>
                JodaInstantEntityValue(storageValue.value.map((date: Date) =>
                    materializeJodaInstant(entityValue.instantClass, date)))
            case (storageValue: DateStorageValue, entityValue: CalendarEntityValue) =>
                CalendarEntityValue(storageValue.value.map((v: Date) => {
                    val calendar = Calendar.getInstance
                    calendar.setTime(v)
                    calendar
                }))
            case (storageValue: ByteArrayStorageValue, entityValue: ByteArrayEntityValue) =>
                ByteArrayEntityValue(storageValue.value)
            case (storageValue: ReferenceStorageValue, entityValue: EntityInstanceEntityValue[_]) =>
                EntityInstanceReferenceValue(storageValue.value.asInstanceOf[Option[Entity#ID]])(entityValue.entityManifest)
            case (stringValue: StringStorageValue, enumerationValue: EnumerationEntityValue[_]) => {
                val value = if (stringValue.value.isDefined) {
                    val enumerationValueClass = enumerationValue.enumerationClass
                    val enumerationClass = enumerationValueClass.getEnclosingClass
                    val enumerationObjectClass = enumerationClass.getClassLoader.loadClass(enumerationClass.getName + "$")
                    val obj = getObject[Enumeration](enumerationObjectClass)
                    Option(obj.withName(stringValue.value.get))
                } else None
                EnumerationEntityValue(value)
            }
            case (storageValue: ByteArrayStorageValue, entityValue: SerializableEntityValue[_]) =>
                SerializableEntityValue[Any](
                    storageValue.value.map(v => entityValue.serializator.fromSerialized(v)(entityValue.typeManifest)))
            case (storageValue: ListStorageValue, entityValue: ListEntityValue[_]) =>
                val v = storageValue.value.map(list => list.map(e => {
                    unmarshalling(e, entityValue.emptyValueEntityValue).value
                }))
                if (entityValue.emptyValueEntityValue.isInstanceOf[EntityInstanceEntityValue[_]])
                    ReferenceListEntityValue[Any](v.asInstanceOf[Option[List[Option[Entity#ID]]]])(entityValue.valueManifest.asInstanceOf[Manifest[Any]], entityValue.tval.asInstanceOf[Option[Any] => EntityValue[Any]])
                else
                    ListEntityValue[Any](v.map(_.map(_.orNull)))(entityValue.valueManifest.asInstanceOf[Manifest[Any]], entityValue.tval.asInstanceOf[Option[Any] => EntityValue[Any]])
            case (storageValue: ListStorageValue, entityValue: LazyListEntityValue[_]) =>
                val v = storageValue.value.map(list => list.collect {
                    case e: ReferenceStorageValue =>
                        e.value.get.asInstanceOf[AnyRef]
                    case e: StringStorageValue =>
                        e.value.get
                }).map { ids =>
                    new LazyList[Entity](ids.asInstanceOf[List[Entity#ID]])(entityValue.valueManifest.asInstanceOf[Manifest[Entity]])
                }
                LazyListEntityValue(v)
            case (storageValue: StorageValue, entityValue: EncoderEntityValue[_, _]) =>
                val anyEncoderEntityValue = entityValue.asInstanceOf[EncoderEntityValue[Any, Any]]
                val tempValue = unmarshalling(storageValue, entityValue.emptyTempValue)
                val value = anyEncoderEntityValue.decode(tempValue.asInstanceOf[EntityValue[Any]])
                EncoderEntityValue[Any, Any](anyEncoderEntityValue.encoder)(value)
            case other =>
                throw new IllegalStateException("Invalid storage value.")
        }

    def marshalling(implicit entityValue: EntityValue[_]): StorageValue =
        entityValue match {
            case value: IntEntityValue =>
                IntStorageValue(value.value)
            case value: LongEntityValue =>
                LongStorageValue(value.value)
            case value: BooleanEntityValue =>
                BooleanStorageValue(value.value)
            case value: CharEntityValue =>
                StringStorageValue(value.value.map(_.toString))
            case value: StringEntityValue =>
                StringStorageValue(value.value)
            case value: FloatEntityValue =>
                FloatStorageValue(value.value)
            case value: DoubleEntityValue =>
                DoubleStorageValue(value.value)
            case value: BigDecimalEntityValue =>
                BigDecimalStorageValue(value.value)
            case value: DateEntityValue =>
                DateStorageValue(value.value)
            case value: JodaInstantEntityValue[_] =>
                DateStorageValue(value.value.map(_.toDate))
            case value: CalendarEntityValue =>
                DateStorageValue(value.value.map(_.getTime))
            case value: ByteArrayEntityValue =>
                ByteArrayStorageValue(value.value)
            case value: EntityInstanceEntityValue[_] =>
                idMarshalling(value.value.map(_.id))
            case value: EntityInstanceReferenceValue[_] =>
                idMarshalling(value.value)
            case value: EnumerationEntityValue[_] =>
                StringStorageValue(value.value.map(_.toString))
            case value: ListEntityValue[_] =>
                ListStorageValue(value.value.map(list => list.map(e => marshalling(value.valueEntityValue(e)))), marshalling(value.emptyValueEntityValue))
            case value: LazyListEntityValue[_] =>
                ListStorageValue(value.value.map(list => list.ids.map(e => idMarshalling(Some(e)))), marshalling(value.emptyValueEntityValue))
            case value: SerializableEntityValue[_] =>
                ByteArrayStorageValue(value.value.map(v => value.serializator.toSerialized(v)(value.typeManifest)))
            case value: EncoderEntityValue[_, _] =>
                marshalling(value.encodedEntityValue)
        }

    def marshalling(action: StorageAction): ModifyStorageAction =
        action match {
            case action: CreateTable =>
                StorageCreateTable(action.tableName, marshalling(action.columns), action.onlyIfNotExists)
            case action: CreateListTable =>
                StorageCreateListTable(action.ownerTableName, action.listTableName, marshalling(action.valueColumn), StorageColumn("POS", new IntStorageValue(None), None), action.onlyIfNotExists)
            case action: RemoveListTable =>
                StorageRemoveListTable(action.listTableName, action.onlyIfExists)
            case action: RenameTable =>
                StorageRenameTable(action.oldName, action.newName, action.onlyIfExists)
            case action: RemoveTable =>
                StorageRemoveTable(action.name, action.onlyIfExists, action.isCascade)
            case action: AddColumn =>
                StorageAddColumn(action.tableName, marshalling(action.column), action.onlyIfNotExists)
            case action: RenameColumn =>
                StorageRenameColumn(action.tableName, action.oldName, marshalling(action.column), action.onlyIfExists)
            case action: ModifyColumnType =>
                StorageModifyColumnType(action.tableName, marshalling(action.column), action.onlyIfExists)
            case action: RemoveColumn =>
                StorageRemoveColumn(action.tableName, action.name, action.onlyIfExists)
            case action: AddIndex =>
                StorageAddIndex(action.tableName, action.columnName, action.indexName, action.onlyIfNotExists, action.unique)
            case action: RemoveIndex =>
                StorageRemoveIndex(action.tableName, action.columnName, action.name, action.onlyIfExists)
            case action: AddReference =>
                StorageAddReference(action.tableName, action.columnName, action.referencedTable, action.constraintName, action.onlyIfNotExists)
            case action: RemoveReference =>
                StorageRemoveReference(action.tableName, action.columnName, action.referencedTable, action.constraintName, action.onlyIfExists)
        }

    def marshalling(columns: List[Column[_]]): List[StorageColumn] =
        columns.map(marshalling)

    def marshalling(column: Column[_]): StorageColumn =
        StorageColumn(column.name, marshalling(column.emptyEntityValue), column.specificTypeOption)

    def idMarshalling(entityId: Option[Entity#ID]): ReferenceStorageValue = {
        val value =
            entityId.map { id =>
                val entityValue = EntityValue.tvalFunction(id.getClass, classOf[Object])(entityId)
                marshalling(entityValue)
            }
        ReferenceStorageValue(value)
    }
}

case class StorageColumn(name: String, storageValue: StorageValue, specificTypeOption: Option[String])
sealed trait ModifyStorageAction
case class StorageCreateTable(tableName: String, columns: List[StorageColumn], ifNotExists: Boolean) extends ModifyStorageAction
case class StorageCreateListTable(ownerTableName: String, listTableName: String, valueColumn: StorageColumn, orderColumn: StorageColumn, ifNotExists: Boolean) extends ModifyStorageAction {
    val addOwnerIndexAction = StorageAddIndex(listTableName, "owner", "own_idx_" + listTableName, ifNotExists, false)
}
case class StorageRemoveListTable(listTableName: String, ifExists: Boolean) extends ModifyStorageAction
case class StorageRenameTable(oldName: String, newName: String, ifExists: Boolean) extends ModifyStorageAction
case class StorageRemoveTable(name: String, ifExists: Boolean, cascade: Boolean) extends ModifyStorageAction
case class StorageAddColumn(tableName: String, column: StorageColumn, ifNotExists: Boolean) extends ModifyStorageAction
case class StorageRenameColumn(tableName: String, oldName: String, column: StorageColumn, ifExists: Boolean) extends ModifyStorageAction
case class StorageModifyColumnType(tableName: String, column: StorageColumn, ifExists: Boolean) extends ModifyStorageAction
case class StorageRemoveColumn(tableName: String, name: String, ifExists: Boolean) extends ModifyStorageAction
case class StorageAddIndex(tableName: String, columnName: String, indexName: String, ifNotExists: Boolean, unique: Boolean) extends ModifyStorageAction
case class StorageRemoveIndex(tableName: String, columnName: String, name: String, ifExists: Boolean) extends ModifyStorageAction
case class StorageAddReference(tableName: String, columnName: String, referencedTable: String, constraintName: String, ifNotExists: Boolean) extends ModifyStorageAction
case class StorageRemoveReference(tableName: String, columnName: String, referencedTable: String, constraintName: String, ifExists: Boolean) extends ModifyStorageAction
