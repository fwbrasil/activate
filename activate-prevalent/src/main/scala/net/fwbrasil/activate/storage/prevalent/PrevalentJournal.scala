package net.fwbrasil.activate.storage.prevalent

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.serialization.Serializer
import java.nio.ByteOrder

class PrevalentJournal(directory: File, serializer: Serializer, fileSize: Int, bufferPoolSize: Int) {

    private val fileSeparator = System.getProperty("file.separator")

    directory.mkdirs

    private var (currentBuffer, currentBufferIdx) = {
        val buffers = directoryBuffers
        if (buffers.isEmpty)
            (createBuffer(0), 0)
        else {
            buffers.reverse.tail.foreach(byteBufferCleaner.cleanDirect)
            (buffers.last, buffers.size - 1)
        }
    }

    private val bufferPool = new DirectBufferPool(fileSize, bufferPoolSize)

    Runtime.getRuntime.addShutdownHook(
        new Thread {
            override def run = {
                byteBufferCleaner.cleanDirect(currentBuffer)
                currentBuffer = null
                bufferPool.destroy
            }
        })

    def add(transaction: PrevalentTransaction): Unit = {
        val temp = bufferPool.pop
        try {
            serializeToTempBuffer(temp, transaction)
            val totalSize = temp.remaining
            val writeBuffer = createWriteBuffer(totalSize)
            writeBuffer.put(temp)
        } finally {
            temp.clear
            bufferPool.push(temp)
        }
    }

    def recover(system: PrevalentStorageSystem)(implicit ctx: ActivateContext) =
        for (buffer <- directoryBuffers) yield {
            recoverTransactions(system, buffer)
            byteBufferCleaner.cleanDirect(currentBuffer)
            currentBuffer = buffer
        }

    private def serializeToTempBuffer(temp: ByteBuffer, transaction: PrevalentTransaction) = {
        prevalentTransactionSerializer.write(transaction)(temp)
        val totalSize = temp.position
        temp.limit(temp.position)
        temp.rewind
        temp
    }

    private def directoryBuffers = {
        val files = journalFiles
        verifyJournalFiles(files)
        files.map(f => fileToBuffer(f, f.length))
    }

    private def createBuffer(fileIdx: Int) =
        fileToBuffer(new File(newFilePath(fileIdx)), fileSize)

    private def fileName(idx: Int) =
        idx.formatted("%020d") + ".journal"

    private def fileToBuffer(file: File, size: Long) =
        new RandomAccessFile(file, "rw").getChannel
            .map(FileChannel.MapMode.READ_WRITE, 0, size)
            .order(ByteOrder.nativeOrder)

    private def createWriteBuffer(totalSize: Int) =
        synchronized {
            val buffer = createNewBufferIfNecessary(totalSize)
            try buffer.duplicate
            finally buffer.position(buffer.position + totalSize)
        }

    private def newFilePath(fileIdx: Int) =
        directory + fileSeparator + fileName(fileIdx)

    private def recoverTransactions(
        system: PrevalentStorageSystem,
        buffer: ByteBuffer)(implicit ctx: ActivateContext) = {
        var transactionOption = prevalentTransactionSerializer.read(buffer)
        while (transactionOption.isDefined) {
            transactionOption.get.recover(system)
            transactionOption = prevalentTransactionSerializer.read(buffer)
        }
    }

    private def readTransactionBytes(buffer: ByteBuffer) = {
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
    