package net.fwbrasil.activate.multipleVms

import net.fwbrasil.activate.postgresqlContext._

class CustomEncodedEntityValue(val i: Int) extends Serializable

class CustomEncodedEntityValueEncoder
        extends Encoder[CustomEncodedEntityValue, Int] {
    def encode(value: CustomEncodedEntityValue) = value.i
    def decode(i: Int) = new CustomEncodedEntityValue(i)
}