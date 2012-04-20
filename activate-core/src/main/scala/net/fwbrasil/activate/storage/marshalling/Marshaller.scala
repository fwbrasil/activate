package net.fwbrasil.activate.storage.marshalling

import java.util.{ Date, Calendar }
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.Reflection.get
import net.fwbrasil.activate.util.Reflection.getObject
import net.fwbrasil.activate.util.Reflection.materializeJodaInstant
import org.joda.time.base.AbstractInstant
import net.fwbrasil.activate.query.QueryEntityValue
import net.fwbrasil.activate.entity.EnumerationEntityValue
import net.fwbrasil.activate.entity.DateEntityValue
import net.fwbrasil.activate.query.QueryEntitySourceValue
import net.fwbrasil.activate.entity.FloatEntityValue
import net.fwbrasil.activate.query.QueryEntitySourcePropertyValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.CharEntityValue
import net.fwbrasil.activate.entity.DoubleEntityValue
import net.fwbrasil.activate.entity.JodaInstantEntityValue
import net.fwbrasil.activate.query.QuerySelectValue
import net.fwbrasil.activate.query.SimpleValue
import net.fwbrasil.activate.entity.StringEntityValue
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.BooleanEntityValue
import net.fwbrasil.activate.query.QueryMocks
import net.fwbrasil.activate.entity.ByteArrayEntityValue
import net.fwbrasil.activate.query.QueryEntityInstanceValue
import net.fwbrasil.activate.entity.BigDecimalEntityValue
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.CalendarEntityValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.SerializableEntityValue
import net.fwbrasil.activate.serialization.javaSerializator

object Marshaller {

	def unmarshalling(storageValue: StorageValue, entityValue: EntityValue[_]): EntityValue[_] =
		(storageValue, entityValue) match {
			case (storageValue: IntStorageValue, entityValue: IntEntityValue) =>
				IntEntityValue(storageValue.value)
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
				JodaInstantEntityValue(storageValue.value.map((date: Date) => materializeJodaInstant(entityValue.instantClass, date)))
			case (storageValue: DateStorageValue, entityValue: CalendarEntityValue) =>
				CalendarEntityValue(storageValue.value.map((v: Date) => {
					val calendar = Calendar.getInstance
					calendar.setTime(v)
					calendar
				}))
			case (storageValue: ByteArrayStorageValue, entityValue: ByteArrayEntityValue) =>
				ByteArrayEntityValue(storageValue.value)
			case (storageValue: ReferenceStorageValue, entityValue: EntityInstanceEntityValue[_]) =>
				EntityInstanceReferenceValue(storageValue.value)(entityValue.entityManifest)
			case (stringValue: StringStorageValue, enumerationValue: EnumerationEntityValue[_]) => {
				val value = if (stringValue.value.isDefined) {
					val enumerationValueClass = enumerationValue.enumerationClass
					val enumerationClass = enumerationValueClass.getEnclosingClass
					val enumerationObjectClass = Class.forName(enumerationClass.getName + "$")
					val obj = getObject[Enumeration](enumerationObjectClass)
					Option(obj.withName(stringValue.value.get))
				} else None
				EnumerationEntityValue(None)
			}
			case (storageValue: ByteArrayStorageValue, entityValue: SerializableEntityValue[_]) =>
				SerializableEntityValue[Serializable](storageValue.value.map(javaSerializator.fromSerialized[Serializable]))
			case other =>
				throw new IllegalStateException("Invalid storage value.")
		}

	def marshalling(implicit entityValue: EntityValue[_]): StorageValue =
		entityValue match {
			case value: IntEntityValue =>
				IntStorageValue(value.value)
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
			case value: EntityInstanceEntityValue[Entity] =>
				ReferenceStorageValue(value.value.map(_.id))
			case value: EntityInstanceReferenceValue[Entity] =>
				ReferenceStorageValue(value.value)
			case value: EnumerationEntityValue[_] =>
				StringStorageValue(value.value.map(_.toString))
			case value: SerializableEntityValue[_] =>
				ByteArrayStorageValue(value.value.map(javaSerializator.toSerialized))
		}

}