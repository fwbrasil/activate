package net.fwbrasil.activate.multipleVms

import net.fwbrasil.activate.postgresqlContext._
import net.fwbrasil.activate.entity.ZipEncoder
import java.io.DataOutputStream
import java.io.DataInputStream

class CustomEncodedEntityValue(val i: Int) extends Serializable

class CustomEncodedEntityValueEncoder
        extends Encoder[CustomEncodedEntityValue, Int] {
    def encode(value: CustomEncodedEntityValue) = value.i
    def decode(i: Int) = new CustomEncodedEntityValue(i)
}

class CustomEncodedObjectEntityValue(val i: Int) extends Serializable

object CustomEncodedObjectEntityValueEncoder
        extends Encoder[CustomEncodedObjectEntityValue, Int] {
    def encode(value: CustomEncodedObjectEntityValue) = value.i
    def decode(i: Int) = new CustomEncodedObjectEntityValue(i)
}

sealed trait UserStatus extends Serializable
case object SuperUser extends UserStatus
case object NormalUser extends UserStatus

class UserStatusEncoder extends Encoder[UserStatus, Int] {

    def encode(status: UserStatus) = status match {
       case SuperUser => 1
       case NormalUser => 2
     }

     def decode(value: Int) = value match {
       case 1 => SuperUser
       case 2 => NormalUser
     }
}

case class CompressedEntityValue(string: String)

class CompressedEntityValueEncoder extends ZipEncoder[CompressedEntityValue] {
    
    protected def write(value: CompressedEntityValue, stream: DataOutputStream) = {
        val bytes = value.string.getBytes
        stream.writeInt(bytes.length)
        stream.writeBytes(value.string)
    }
    protected def read(stream: DataInputStream) = {
    	val size = stream.readInt 
        val array = new Array[Byte](size)
        stream.readFully(array)
        val string = new String(array)
        new CompressedEntityValue(string)
    }
}
