package net.fwbrasil.activate.entity

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

import net.fwbrasil.activate.util.ConcurrentCache

trait ZipEncoder[T]
    extends Encoder[T, Array[Byte]] {

    private val deflaterCache = new ConcurrentCache[Deflater]("deflaterCache", 200)
    private val inflaterCache = new ConcurrentCache[Inflater]("inflaterCache", 200)

    protected def compressionLevel = Deflater.BEST_SPEED

    def encode(value: T) =
        withDeflater { deflater =>
            val baos = new ByteArrayOutputStream
            val zip = new DeflaterOutputStream(baos, deflater)
            val buffer = new DataOutputStream(zip)
            write(value, buffer)
            buffer.close
            zip.finish
            zip.close
            baos.close
            baos.toByteArray
        }

    protected def write(value: T, stream: DataOutputStream)

    def decode(bytes: Array[Byte]) =
        withInflater { inflater =>
            val bais = new ByteArrayInputStream(bytes)
            val zip = new InflaterInputStream(bais, inflater)
            val buffer = new DataInputStream(zip)
            val value = read(buffer)
            buffer.close
            zip.close
            bais.close
            value
        }

    protected def read(stream: DataInputStream): T

    private def withDeflater[R](f: Deflater => R) = {
        val deflater = this.deflater
        try f(deflater)
        finally {
            deflater.reset
            deflaterCache.offer(deflater)
        }
    }

    private def deflater = {
        val deflater = deflaterCache.poll
        if (deflater == null)
            new Deflater(compressionLevel)
        else
            deflater
    }

    private def withInflater[R](f: Inflater => R) = {
        val inflater = this.inflater
        try f(inflater)
        finally {
            inflater.reset
            inflaterCache.offer(inflater)
        }
    }

    private def inflater = {
        val inflater = inflaterCache.poll
        if (inflater == null)
            new Inflater
        else
            inflater
    }
}