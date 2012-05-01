package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.EntityValue
import java.util.Date

abstract class StorageValue(val value: Option[_]) extends Serializable

case class IntStorageValue(override val value: Option[Int])
	extends StorageValue(value)

case class LongStorageValue(override val value: Option[Long])
	extends StorageValue(value)

case class BooleanStorageValue(override val value: Option[Boolean])
	extends StorageValue(value)

case class StringStorageValue(override val value: Option[String])
	extends StorageValue(value)

case class FloatStorageValue(override val value: Option[Float])
	extends StorageValue(value)

case class DoubleStorageValue(override val value: Option[Double])
	extends StorageValue(value)

case class BigDecimalStorageValue(override val value: Option[BigDecimal])
	extends StorageValue(value)

case class DateStorageValue(override val value: Option[Date])
	extends StorageValue(value)

case class ByteArrayStorageValue(override val value: Option[Array[Byte]])
	extends StorageValue(value)

case class ReferenceStorageValue(override val value: Option[String])
	extends StorageValue(value)

