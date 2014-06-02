package net.fwbrasil.activate.storage.relational.async

import java.sql.Timestamp

import com.twitter.finagle.exp.mysql.BigDecimalValue
import com.twitter.finagle.exp.mysql.ByteValue
import com.twitter.finagle.exp.mysql.DoubleValue
import com.twitter.finagle.exp.mysql.EmptyValue
import com.twitter.finagle.exp.mysql.FloatValue
import com.twitter.finagle.exp.mysql.IntValue
import com.twitter.finagle.exp.mysql.LongValue
import com.twitter.finagle.exp.mysql.NullValue
import com.twitter.finagle.exp.mysql.RawValue
import com.twitter.finagle.exp.mysql.ShortValue
import com.twitter.finagle.exp.mysql.StringValue
import com.twitter.finagle.exp.mysql.Value
import com.twitter.finagle.exp.mysql.transport.BufferReader

import net.fwbrasil.activate.storage.relational.idiom.ActivateResultSet

class FinagleResultSet(values: List[Value]) extends ActivateResultSet {

    def getString(i: Int) =
        valueCase(i) {
            case StringValue(value) => value
        }
    def getBytes(i: Int) =
        valueCase(i) {
            case RawValue(typ, charset, isBinary, bytes) => bytes
        }
    def getInt(i: Int) =
        valueCase(i) {
            case IntValue(value) => value
        }
    def getBoolean(i: Int) =
        valueCase(i) {
            case ByteValue(value) => value == 1.byteValue
            case ShortValue(value) => value == 1.shortValue
        }
    def getFloat(i: Int) =
        valueCase(i) {
            case IntValue(value) => value
            case LongValue(value) => value
            case FloatValue(value) => value
            case DoubleValue(value) => value.floatValue
        }
    def getLong(i: Int) =
        valueCase(i) {
            case RawValue(12, charset, isBinary, bytes) =>
                BufferReader(bytes).readInt
            case IntValue(value) => value
            case LongValue(value) => value
        }
    def getTimestamp(i: Int) =
        valueCase(i) {
            case LongValue(value) => new Timestamp(value)
        }
    def getDouble(i: Int) =
        valueCase(i) {
            case DoubleValue(value) => value
        }
    def getBigDecimal(i: Int) =
        valueCase(i) {
            case BigDecimalValue(value) => value.bigDecimal
        }

    private def valueCase[T](i: Int)(f: PartialFunction[Any, T]): Option[T] = {
        val value = values(i)
        if (value == NullValue || value == EmptyValue)
            None
        else if (!f.isDefinedAt(value))
            throw new IllegalStateException("Invalid value")
        else
            Option(f(value))
    }
}
