package net.fwbrasil.activate

import memoryContext._
import net.fwbrasil.activate.entity.Encoder

class CustomEncodedEntityValue(val i: Int)

class CustomEncodedEntityValueEncoder
        extends Encoder[CustomEncodedEntityValue, Int] {
    def encode(value: CustomEncodedEntityValue) = value.i
    def decode(i: Int) = new CustomEncodedEntityValue(i)
}