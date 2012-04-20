package net.fwbrasil.activate.serialization

trait Serializator {

	def toSerialized[T](value: T): Array[Byte]
	def fromSerialized[T](bytes: Array[Byte]): T
}