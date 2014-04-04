package net.fwbrasil.activate.storage.relational.async

import net.fwbrasil.activate.storage.relational.idiom.ActivateResultSet
import com.github.mauricio.async.db.RowData
import java.sql.Timestamp
import org.joda.time.DateTime
import org.joda.time.LocalDateTime

case class JdbcRelationalAsyncResultSet(rowData: RowData, charset: String)
    extends ActivateResultSet {

    def getString(i: Int) =
        value[String](i)
    def getBytes(i: Int) =
        value[Array[Byte]](i)
    def getInt(i: Int) =
        valueCase[Int](i) {
            case n =>
                n.toString.toInt
        }
    def getBoolean(i: Int) =
        valueCase[Boolean](i) {
            case boolean: Boolean =>
                boolean
            case byte: Byte =>
                byte == (1: Byte)
        }
    def getFloat(i: Int) =
        valueCase[Float](i) {
            case n =>
                n.toString.toFloat
        }
    def getLong(i: Int) =
        valueCase[Long](i) {
            case localDateTime: LocalDateTime =>
                localDateTime.toDate.getTime
            case dateTime: DateTime =>
                dateTime.toDate.getTime
            case n =>
                n.toString.toLong
        }
    def getTimestamp(i: Int) =
        valueCase[Timestamp](i) {
            case localDateTime: LocalDateTime =>
                new Timestamp(localDateTime.toDateTime.getMillis)
            case dateTime: DateTime =>
                new Timestamp(dateTime.getMillis)
        }
    def getDouble(i: Int) =
        valueCase[Double](i) {
            case n =>
                n.toString.toDouble
        }
    def getBigDecimal(i: Int) =
        valueCase[java.math.BigDecimal](i) {
            case n: BigDecimal =>
                n.bigDecimal
        }

    private def value[T](i: Int): Option[T] =
        Option(rowData(i).asInstanceOf[T])

    private def valueCase[T](i: Int)(f: PartialFunction[Any, T]): Option[T] = {
        val value = rowData(i)
        if (value == null)
            None
        else if (!f.isDefinedAt(value))
            throw new IllegalStateException("Invalid value")
        else
            Option(f(value))
    }
}