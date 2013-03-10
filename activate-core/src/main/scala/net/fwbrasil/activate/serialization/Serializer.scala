package net.fwbrasil.activate.serialization

trait Serializer extends Serializable {

    def toSerialized[T: Manifest](value: T): Array[Byte]
    def fromSerialized[T: Manifest](bytes: Array[Byte]): T
}