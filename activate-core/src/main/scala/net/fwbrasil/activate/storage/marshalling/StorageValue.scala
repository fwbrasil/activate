package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity.EntityValue
import java.util.Date
import java.io.OutputStream

trait StorageValue extends Serializable {
    type V
    val value: V
}

trait StorageOptionalValue extends StorageValue {
    type T
    type V = Option[T]
}

case class IntStorageValue(override val value: Option[Int])
    extends StorageOptionalValue {
    type T = Int
}

case class LongStorageValue(override val value: Option[Long])
    extends StorageOptionalValue {
    type T = Long
}

case class BooleanStorageValue(override val value: Option[Boolean])
    extends StorageOptionalValue {
    type T = Boolean
}

case class StringStorageValue(override val value: Option[String])
    extends StorageOptionalValue {
    type T = String
}

case class FloatStorageValue(override val value: Option[Float])
    extends StorageOptionalValue {
    type T = Float
}

case class DoubleStorageValue(override val value: Option[Double])
    extends StorageOptionalValue {
    type T = Double
}

case class BigDecimalStorageValue(override val value: Option[BigDecimal])
    extends StorageOptionalValue {
    type T = BigDecimal
}

case class DateStorageValue(override val value: Option[Date])
    extends StorageOptionalValue {
    type T = Date
}

case class ByteArrayStorageValue(override val value: Option[Array[Byte]])
    extends StorageOptionalValue {
    type T = Array[Byte]
}

case class ListStorageValue(override val value: Option[List[StorageValue]], val emptyStorageValue: StorageValue)
    extends StorageOptionalValue {
    type T = List[StorageValue]
}

case class ReferenceStorageValue(override val value: StorageOptionalValue)
    extends StorageValue {
    type V = StorageOptionalValue
}
