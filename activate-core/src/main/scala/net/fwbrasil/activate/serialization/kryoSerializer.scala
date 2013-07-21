package net.fwbrasil.activate.serialization

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.{ Serializer => KryoSerializer }
import org.objenesis.strategy.StdInstantiatorStrategy
import java.nio.ByteBuffer

object kryoSerializer extends Serializer {

    def toSerialized[T: Manifest](value: T): Array[Byte] = {
        val baos = new ByteArrayOutputStream();
        val output = new Output(baos)
        newKryo.writeObject(output, value)
        output.close
        baos.toByteArray()
    }
    def fromSerialized[T: Manifest](bytes: Array[Byte]): T = {
        val bais = new ByteArrayInputStream(bytes);
        val input = new Input(bais)
        val obj = newKryo.readObject(input, manifest[T].runtimeClass)
        input.close
        obj.asInstanceOf[T]
    }

    private def newKryo: com.esotericsoftware.kryo.Kryo = {
        val kryo = new Kryo
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy)
        kryo.register(None.getClass, noneSerializer)
        kryo.register(classOf[Some[_]], someSerializer)
        kryo
    }
}

object noneSerializer extends KryoSerializer[None.type] {
    def read(kryo: Kryo, input: Input, clazz: Class[None.type]): None.type = None
    def write(kryo: Kryo, output: Output, clazz: None.type): Unit = {}
}

object someSerializer extends KryoSerializer[Some[_]] {
    def read(kryo: Kryo, input: Input, clazz: Class[Some[_]]): Some[_] = Some(kryo.readClassAndObject(input))
    def write(kryo: Kryo, output: Output, value: Some[_]): Unit = kryo.writeClassAndObject(output, value.get)
}