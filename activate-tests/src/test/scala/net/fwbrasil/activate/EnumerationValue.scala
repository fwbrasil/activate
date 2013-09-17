package net.fwbrasil.activate

object EnumerationValue extends Enumeration {
    case class EnumerationValue(name: String) extends Val(name)
    val value1a = EnumerationValue("v1")
    val value2 = EnumerationValue("v2")
    val value3 = EnumerationValue("v3")
}