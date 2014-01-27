package net.fwbrasil.activate.multipleVms

import net.fwbrasil.activate.postgresqlContext._

class CustomEncodedEntityValue(val i: Int) extends Serializable

class CustomEncodedEntityValueEncoder
        extends Encoder[CustomEncodedEntityValue, Int] {
    def encode(value: CustomEncodedEntityValue) = value.i
    def decode(i: Int) = new CustomEncodedEntityValue(i)
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