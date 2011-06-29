package net.fwbrasil.activate.serialization

trait Serializator {

	def toSerialized[T: Manifest](value: T): Array[Byte]
	def fromSerialized[T: Manifest](bytes: Array[Byte]): T
}