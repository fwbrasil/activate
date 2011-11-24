package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.EntityValue
import java.util.Date

abstract class StorageValue(val value: Option[_])(implicit val entityValue: EntityValue[_]) extends Serializable

case class IntStorageValue(override val value: Option[Int])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class BooleanStorageValue(override val value: Option[Boolean])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class StringStorageValue(override val value: Option[String])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class FloatStorageValue(override val value: Option[Float])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class DoubleStorageValue(override val value: Option[Double])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class BigDecimalStorageValue(override val value: Option[BigDecimal])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class DateStorageValue(override val value: Option[Date])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class ByteArrayStorageValue(override val value: Option[Array[Byte]])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

case class ReferenceStorageValue(override val value: Option[String])(override implicit val entityValue: EntityValue[_])
  extends StorageValue(value)(entityValue)

