package net.fwbrasil.scala

import scala.runtime.ScalaRunTime

trait CustomTuple {
	this: Product =>

	override def toString() = "(" + this.productIterator.mkString(", ") + ")"

	override def hashCode(): Int = ScalaRunTime._hashCode(this)

	override def equals(other: Any): Boolean =
		if (canEqual(other)) {
			val otherTuple = other.asInstanceOf[Tuple24[_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _]]
			(0 until this.productArity).forall(i => this.productElement(i) == otherTuple.productElement(i))
		} else false

}