package net.fwbrasil.activate.storage.prevalent

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.serialization.Serializer
import java.nio.ByteOrder
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import java.io.ObjectInputStream
import java.io.FileInputStream

class PrevalentJournal(directory: File, serializer: Serializer, fileSize: Int, bufferPoolSize: Int) {

    private val fileSeparator = System.getProperty("file.separator")

    directory.mkdirs

    private var currentBuffer: ByteBuffer = null
    private var currentBufferIdx = 0

    private val bufferPool = new DirectBufferPool(fileSize, bufferPoolSize)

    Runtime.getRuntime.addShutdownHook(
        new Thread {
            override def run = {
                cleanCurrentBuffer
                bufferPool.destroy
            }
        })

    private def clear = {
        cleanCurrentBuffer
        currentBufferIdx = 0
    }

    private def cleanCurrentBuffer =
        if (currentBuffer != null) {
            byteBufferCleaner.cleanDirect(currentBuffer)
            currentBuffer = null
        }

    def takeSnapshot(system: PrevalentStorageSystem) = {
        val snapshotFile = new File(directory, fileName(nextFileIndex, ".snapshot"))
        require(snapshotFile.createNewFile)
        val stream = new ObjectOutputStream(new FileOutputStream(snapshotFile))
        stream.writeObject(system)
        stream.close
    }

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

    def recover(implicit ctx: ActivateContext) = {
        clear
        val (nextFileId, system) = recoverSnapshot
        val buffers = directoryBuffers(nextFileId)
        for (buffer <- buffers) yield {
            recoverTransactions(system, buffer)
            byteBufferCleaner.cleanDirect(currentBuffer)
            currentBuffer = buffer
        }
        if (buffers.isEmpty) {
            currentBuffer = createBuffer(nextFileId)
            currentBufferIdx = nextFileId + buffers.size
        } else
            currentBufferIdx = nextFileId + buffers.size - 1
        system
    }

    private def recoverSnapshot(implicit ctx: ActivateContext) =
        snapshotFiles.lastOption.map { file =>
            val stream = new ObjectInputStream(new FileInputStream(file))
            val system = stream.readObject.asInstanceOf[PrevalentStorageSystem]

            val idx = fileNameToIndex(file)
            (idx + 1, system)
        }.getOrElse((0, new PrevalentStorageSystem))

    private def serializeToTempBuffer(temp: ByteBuffer, transaction: PrevalentTransaction) = {
        prevalentTransactionSerializer.write(transaction)(temp)
        val totalSize = temp.position
        temp.limit(temp.position)
        temp.rewind
        temp
    }

    private def directoryBuffers(startIdx: Int) = {
        val files = journalFiles.filter(file => fileNameToIndex(file) >= startIdx)
        verifyJournalFiles(files, startIdx)
        files.map(f => fileToBuffer(f, f.length))
    }

    private def createBuffer(fileIdx: Int) =
        fileToBuffer(new File(newFilePath(fileIdx)), fileSize)

    private def fileName(idx: Int, sufix: String = ".journal") =
        idx.formatted("%020d") + sufix

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
            currentBuffer = createBuffer(nextFileIndex)
        }
        currentBuffer
    }

    private def nextFileIndex = {
        currentBufferIdx += 1
        currentBufferIdx
    }

    private def fileNameToIndex(file: File) =
        file.getName
            .replace(".journal", "")
            .replace(".snapshot", "")
            .toInt

    private def journalFiles =
        files.filter(_.getName.endsWith(".journal"))

    private def snapshotFiles =
        files.filter(_.getName.endsWith(".snapshot"))

    private def files =
        directory
            .listFiles
            .toList
            .sortBy(_.getName)

    private def verifyJournalFiles(files: List[File], startIdx: Int) =
        for (idx <- startIdx until files.size)
            if (fileNameToIndex(files(idx)) != idx)
                throw new IllegalStateException(s"Can't find ${fileName(idx)}")

}
    