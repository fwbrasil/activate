package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.ManifestUtil._

abstract class Encoder[A, B](implicit val tval: (Option[B]) => EntityValue[B], val m: Manifest[A]) {

    def entityValue(value: Option[A]) = EncoderEntityValue(this)(value)
    def clazz = erasureOf[A]
    
    def encode(value: A): B
    def decode(value: B): A

}

case class EncoderEntityValue[A: Manifest, B](encoder: Encoder[A, B])(value: Option[A]) extends EntityValue[A](value) {
    def emptyValue = null.asInstanceOf[A]
    def emptyTempValue = encoder.tval(None)
    def encodedEntityValue = encoder.tval(value.map(encoder.encode))
    def decode(value: EntityValue[B]) = value.value.map(encoder.decode)
}