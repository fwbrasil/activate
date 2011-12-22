package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query._
import java.util.{Date, Calendar}
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.util.Reflection.newInstance

object Marshaller {
	

	
	def marshalling(value: QuerySelectValue[_]): StorageValue =
		value match {
			case value: QueryEntityValue[_] =>
				marshalling(value)
			case value: SimpleValue[_] =>
				marshalling(value)
		}

	def marshalling[V](value: QueryEntityValue[V]): StorageValue =
		value match {
			case value: QueryEntityInstanceValue[V] =>
				marshalling(value)
			case value: QueryEntitySourceValue[V] =>
				marshalling(value)
		}
	
	def marshalling[V <: Entity: Manifest](value: QueryEntityInstanceValue[V]): StorageValue =
		marshalling(EntityInstanceEntityValue[V](Option(value.entity)))

	def marshalling[V](value: QueryEntitySourceValue[V]): StorageValue =
		value match {
			case value: QueryEntitySourcePropertyValue[V] =>
				marshalling(value)
			case value: QueryEntitySourceValue[V] =>
				marshalling(EntityInstanceEntityValue(None)(manifestClass(value.entitySource.entityClass)))
		}
	
	def marshalling[P](value: QueryEntitySourcePropertyValue[P]): StorageValue =
		marshalling(value.lastVar.asInstanceOf[QueryMocks.FakeVarToQuery[_]].entityValueMock)
	
	def marshalling(value: SimpleValue[_]): StorageValue =
		marshalling(value.entityValue)
		
	def unmarshalling(storageValue: StorageValue): EntityValue[_] = 
		(storageValue, storageValue.entityValue) match {
			case (storageValue: IntStorageValue, entityValue: IntEntityValue) =>
				IntEntityValue(storageValue.value)
			case (storageValue: BooleanStorageValue, entityValue: BooleanEntityValue) =>
				BooleanEntityValue(storageValue.value)
			case (storageValue: StringStorageValue, entityValue: CharEntityValue) =>
				CharEntityValue(transform(storageValue, (v: String) => v.charAt(0)))
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
			case (storageValue: DateStorageValue, entityValue: CalendarEntityValue) =>
				CalendarEntityValue(transform(storageValue, (v: Date) => {
					val calendar = Calendar.getInstance
					calendar.setTime(v)
					calendar
				}))
			case (storageValue: ByteArrayStorageValue, entityValue: ByteArrayEntityValue) =>
				ByteArrayEntityValue(storageValue.value)
			case (storageValue: ReferenceStorageValue, entityValue: EntityInstanceEntityValue[_]) =>
				EntityInstanceReferenceValue(storageValue.value)(entityValue.entityManifest)
			case other =>
				throw new IllegalStateException("Invalid storage value.")
		}
	
	def marshalling(implicit entityValue: EntityValue[_]): StorageValue =
		(entityValue match {
			case value: IntEntityValue =>
				IntStorageValue(value.value)
			case value: BooleanEntityValue =>
				BooleanStorageValue(value.value)
			case value: CharEntityValue =>
				StringStorageValue(transform(value, (v: Char) => v.toString))
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
			case value: CalendarEntityValue =>
				DateStorageValue(transform(value, (v: Calendar) => v.getTime))
			case value: ByteArrayEntityValue =>
				ByteArrayStorageValue(value.value)
			case value: EntityInstanceEntityValue[Entity] =>
				ReferenceStorageValue(transform(value, (v: Entity) => v.id))
			case value: EntityInstanceReferenceValue[Entity] =>
				ReferenceStorageValue(value.value)
		}).asInstanceOf[StorageValue]
	
	private[this] def transform[V, R](entityValue: EntityValue[V], f: (V) => R): Option[R] =
		if(entityValue.value == None)
			None
		else
			Option(f(entityValue.value.get))
	
	private[this] def transform[V, R](storageValue: StorageValue, f: (V) => R): Option[R] =
		if(storageValue.value == None)
			None
		else
			Option(f(storageValue.value.get.asInstanceOf[V]))
}