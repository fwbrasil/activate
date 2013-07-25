package net.fwbrasil.activate.storage.prevalent

import java.io.File
import net.fwbrasil.activate.serialization.Serializer
import java.nio.MappedByteBuffer
import java.io.FilenameFilter
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import scala.collection.mutable.ListBuffer
import org.apache.commons.collections.BufferUnderflowException
import net.fwbrasil.activate.ActivateContext

class PrevalentJournal(directory: File, serializer: Serializer, fileSize: Int) {

    private val fileSeparator = System.getProperty("file.separator")

    private var (currentBuffer, currentBufferIdx) = {
        val buffers = directoryBuffers
        if (buffers.isEmpty)
            (createBuffer(0), 0)
        else
            (buffers.last, buffers.size - 1)
    }

    def add(transaction: PrevalentTransaction): Unit =
        write(serializer.toSerialized(transaction))

    def recover(system: PrevalentStorageSystem)(implicit ctx: ActivateContext) =
        for (buffer <- directoryBuffers) yield {
            buffer.get // why there is this empty byte?
            recoverTransactions(system, buffer)
            currentBuffer = buffer
        }

    private def directoryBuffers = {
        val files = journalFiles
        verifyJournalFiles(files)
        files.map(f => fileToBuffer(f, f.length))
    }

    private def hasTransactionToRecover(buffer: MappedByteBuffer) =
        try {
            buffer.mark
            val i = buffer.getInt
            i > 0
        } catch {
            case e: BufferUnderflowException =>
                false
        } finally
            buffer.reset

    private def write(bytes: Array[Byte]) = {
        val bytesSize = bytes.length
        val totalSize = bytesSize + 4
        verifyFileSize(totalSize)
        val writeBuffer = createWriteBuffer(totalSize)
        writeBuffer.putInt(bytesSize)
        writeBuffer.put(bytes)
    }

    private def createBuffer(fileIdx: Int) =
        fileToBuffer(new File(newFilePath(fileIdx)), fileSize)

    private def fileName(idx: Int) =
        idx.formatted("%020d") + ".journal"

    private def fileToBuffer(file: File, size: Long) =
        new RandomAccessFile(file, "rw").getChannel
            .map(FileChannel.MapMode.READ_WRITE, 0, size)

    private def verifyFileSize(totalSize: Int) =
        if (fileSize < totalSize)
            throw new IllegalStateException("Prevalent file size is lesser than the transaction size.")

    private def createWriteBuffer(totalSize: Int) =
        synchronized {
            val buffer =
                createNewBufferIfNecessary(totalSize)
            try buffer.duplicate
            finally buffer.position(buffer.position + totalSize)
        }

    private def newFilePath(fileIdx: Int) =
        directory + fileSeparator + fileName(fileIdx)

    private def recoverTransactions(
        system: PrevalentStorageSystem,
        buffer: MappedByteBuffer)(implicit ctx: ActivateContext) =
        while (hasTransactionToRecover(buffer))
            readTransaction(buffer).recover(system)

    private def readTransaction(buffer: MappedByteBuffer) =
        serializer.fromSerialized[PrevalentTransaction](
            readTransactionBytes(buffer))

    private def readTransactionBytes(buffer: MappedByteBuffer) = {
        val transactionSize = buffer.getInt
        val bytes = new Array[Byte](transactionSize)
        buffer.get(bytes)
        bytes
    }

    private def createNewBufferIfNecessary(totalSize: Int) = {
        if (currentBuffer.remaining < totalSize) {
            currentBufferIdx += 1
            currentBuffer = createBuffer(currentBufferIdx)
        }
        currentBuffer
    }

    private def journalFiles =
        directory
            .listFiles
            .toList
            .filter(_.getName.endsWith(".journal"))
            .sortBy(_.getName)

    private def verifyJournalFiles(files: List[File]) =
        for (idx <- 0 until files.size)
            if (files(idx).getName != fileName(idx))
                throw new IllegalStateException(s"Can't find ${fileName(idx)}")

}
    