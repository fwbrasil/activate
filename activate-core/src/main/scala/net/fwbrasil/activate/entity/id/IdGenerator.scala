package net.fwbrasil.activate.entity.id

import net.fwbrasil.activate.sequence._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil._
import scala.math.Numeric
import net.fwbrasil.activate.ActivateContext
import scala.annotation.tailrec

abstract class IdGenerator[E <: Entity: Manifest] {
    def entityClass = erasureOf[E]
    def nextId(entityClass: Class[_]): E#ID
}

abstract class SegmentedIdGenerator[E <: Entity: Manifest](
    val sequence: Sequence[E#ID])(
        implicit n: Numeric[E#ID])
        extends IdGenerator[E] {

    private val segmentSize = sequence.step
    private var hi = sequence.nextValue(1)
    private var low = n.zero

    require(segmentSize > 1,
        "The sequence step must be greater than one. " +
            "The step is used as the segment size for the id generation.")

    @tailrec final def nextId(entityClass: Class[_]): E#ID =
        synchronized {
            if (n.lteq(low, n.fromInt(segmentSize)))
                n.plus(hi, low)
            else {
                hi = sequence.nextValue(segmentSize)
                nextId(entityClass)
            }
        }

}

case class SequencedIdGenerator[T](
        sequence: Sequence[T]) {
    def nextId(entityClass: Class[_]) =
        sequence.nextValue(1)
}
