package net.fwbrasil.activate.storage.prevalent

import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import java.nio.ByteBuffer
import java.nio.charset.Charset
import scala.collection.mutable.ListBuffer
import java.util.Date
import java.nio.BufferUnderflowException
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue

object prevalentTransactionSerializer {

    def write(transaction: PrevalentTransaction)(implicit buffer: ByteBuffer) = {
        writeBoolean(true)
        writeValuesArray(transaction.insertList)
        writeValuesArray(transaction.updateList)
        writeStringArray(transaction.deleteList)
    }

    def read(implicit buffer: ByteBuffer) =
        if (hasTrue) {
            buffer.get
            Some(
                new PrevalentTransaction(
                    insertList = readValuesArray,
                    updateList = readValuesArray,
                    deleteList = readStringArray))
        } else 
            None

    private def hasTrue(implicit buffer: ByteBuffer) =
        try {
            buffer.mark
            buffer.get == Byte.MaxValue
        } catch {
            case e: BufferUnderflowException =>
                false
        } finally
            buffer.reset

    private def writeStringArray(array: Array[String])(implicit buffer: ByteBuffer) = {
        buffer.putInt(array.length)
        array.foreach(writeString)
    }

    private def readStringArray(implicit buffer: ByteBuffer) = {
        val length = buffer.getInt
        val array = new Array[String](length)
        for (i <- 0 until length)
            array(i) = readString
        array
    }

    private def writeValuesArray(array: Array[(String, Map[String, StorageValue])])(implicit buffer: ByteBuffer) = {
        buffer.putInt(array.size)
        array.foreach(writeTuple)
    }

    private def readValuesArray(implicit buffer: ByteBuffer) = {
        val length = buffer.getInt
        val array = new Array[(String, Map[String, StorageValue])](length)
        for (i <- 0 until length)
            array(i) = readTuple
        array
    }

    private def writeTuple(tuple: (String, Map[String, StorageValue]))(implicit buffer: ByteBuffer) = {
        writeString(tuple._1)
        writeMap(tuple._2)
    }

    private def readTuple(implicit buffer: ByteBuffer) =
        (readString, readMap)

    private def writeString(string: String)(implicit buffer: ByteBuffer) = {
        buffer.putInt(string.length)
        buffer.put(string.getBytes)
    }

    private def readString(implicit buffer: ByteBuffer) = {
        val length = buffer.getInt
        val bytes = new Array[Byte](length)
        buffer.get(bytes)
        new String(bytes)
    }

    private def writeMap(map: Map[String, StorageValue])(implicit buffer: ByteBuffer) = {
        buffer.putInt(map.size)
        for ((name, value) <- map)
            writeProperty(name, value)
    }

    private def readMap(implicit buffer: ByteBuffer) = {
        val length = buffer.getInt
        val array = new Array[(String, StorageValue)](length)
        for (i <- 0 until length)
            array(i) = readProperty
        array.toMap
    }

    private def writeProperty(name: String, value: StorageValue)(implicit buffer: ByteBuffer) = {
        writeString(name)
        writeValue(value)
    }

    private def readProperty(implicit buffer: ByteBuffer) =
        (readString, readValue)

    private def writeBoolean(value: Boolean)(implicit buffer: ByteBuffer) =
        if (value)
            buffer.put(Byte.MaxValue)
        else
            buffer.put(Byte.MinValue)

    private def readBoolean(implicit buffer: ByteBuffer) =
        buffer.get == Byte.MaxValue

    private def writeValue(value: StorageValue)(implicit buffer: ByteBuffer): Unit =
        value match {
            case value: IntStorageValue =>
                writeValue[Int](0, value.value, buffer.putInt(_))
            case value: LongStorageValue =>
                writeValue[Long](1, value.value, buffer.putLong(_))
            case value: BooleanStorageValue =>
                writeValue[Boolean](2, value.value, writeBoolean(_))
            case value: StringStorageValue =>
                writeValue[String](3, value.value, writeString(_))
            case value: FloatStorageValue =>
                writeValue[Float](4, value.value, buffer.putFloat(_))
            case value: DateStorageValue =>
                writeValue[Long](5, value.value.map(_.getTime), buffer.putLong(_))
            case value: DoubleStorageValue =>
                writeValue[Double](6, value.value, buffer.putDouble(_))
            case value: BigDecimalStorageValue =>
                writeValue[Double](7, value.value.map(_.doubleValue), buffer.putDouble(_))
            case value: ByteArrayStorageValue =>
                writeValue[Array[Byte]](8, value.value, writeByteArray(_))
            case value: ListStorageValue =>
                writeValue[List[StorageValue]](9, value.value, writeList(_))
                writeValue(value.emptyStorageValue)
            case value: ReferenceStorageValue =>
                writeValue[String](10, value.value, writeString(_))
        }

    private def readValue(implicit buffer: ByteBuffer): StorageValue =
        buffer.get.intValue match {
            case 0 =>
                IntStorageValue(readValue(buffer.getInt))
            case 1 =>
                LongStorageValue(readValue(buffer.getLong))
            case 2 =>
                BooleanStorageValue(readValue(readBoolean))
            case 3 =>
                StringStorageValue(readValue(readString))
            case 4 =>
                FloatStorageValue(readValue(buffer.getFloat))
            case 5 =>
                DateStorageValue(readValue(new Date(buffer.getLong)))
            case 6 =>
                DoubleStorageValue(readValue(buffer.getDouble))
            case 7 =>
                BigDecimalStorageValue(readValue(buffer.getDouble))
            case 8 =>
                ByteArrayStorageValue(readValue(readByteArray))
            case 9 =>
                ListStorageValue(readValue(readList), readValue)
            case 10 =>
                ReferenceStorageValue(readValue(readString))
        }

    private def writeList(list: List[StorageValue])(implicit buffer: ByteBuffer) = {
        buffer.putInt(list.length)
        list.foreach(writeValue)
    }

    private def readList(implicit buffer: ByteBuffer) = {
        val length = buffer.getInt
        val array = new Array[StorageValue](length)
        for (i <- 0 until length)
            array(i) = readValue
        array.toList
    }

    private def writeByteArray(array: Array[Byte])(implicit buffer: ByteBuffer) = {
        buffer.putInt(array.length)
        buffer.put(array)
    }
    private def readByteArray(implicit buffer: ByteBuffer) = {
        val length = buffer.getInt
        val array = new Array[Byte](length)
        buffer.get(array)
        array
    }

    private def writeValue[V](identifier: Byte, option: Option[V], writeValue: (V) => Unit)(implicit buffer: ByteBuffer): Unit = {
        buffer.put(identifier)
        option.map { value =>
            writeBoolean(true)
            writeValue(value)
        }.getOrElse {
            writeBoolean(false)
        }
    }

    private def readValue[V](readValue: => V)(implicit buffer: ByteBuffer) =
        if (readBoolean)
            Some(readValue)
        else
            None

}