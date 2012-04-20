package net.fwbrasil.activate.serialization

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class JavaSerializatorEvelope[T](val value: T) extends Serializable

object javaSerializator extends Serializator {

	def toSerialized[T](value: T): Array[Byte] = {
		val envelope = new JavaSerializatorEvelope(value)
		val baos = new ByteArrayOutputStream();
		val oos = new ObjectOutputStream(baos);
		oos.writeObject(envelope);
		baos.toByteArray
	}
	def fromSerialized[T](bytes: Array[Byte]): T = {
		val bios = new ByteArrayInputStream(bytes);
		val ois = new ObjectInputStream(bios);
		val envelope = ois.readObject().asInstanceOf[JavaSerializatorEvelope[T]];
		envelope.value.asInstanceOf[T]
	}
}