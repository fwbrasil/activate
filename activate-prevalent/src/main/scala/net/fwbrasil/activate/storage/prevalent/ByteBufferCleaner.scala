package net.fwbrasil.activate.storage.prevalent

import sun.nio.ch.DirectBuffer
import java.nio.ByteBuffer

object byteBufferCleaner {

    def cleanDirect(buffer: ByteBuffer) = 
        buffer.asInstanceOf[DirectBuffer].cleaner.clean
}