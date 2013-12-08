package net.fwbrasil.activate.entity.id

import net.fwbrasil.activate.sequence._
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.util.ManifestUtil._
import scala.math.Numeric
import net.fwbrasil.activate.ActivateContext
import scala.annotation.tailrec

abstract class IdGenerator[E <: BaseEntity: Manifest] {
    def entityClass = erasureOf[E]
    def nextId: E#ID
}

abstract class SegmentedIdGenerator[E <: BaseEntity: Manifest](
    fSequence: => Sequence[E#ID])(
        implicit n: Numeric[E#ID],
        ctx: ActivateContext)
        extends IdGenerator[E] {

    import ctx._

    private val sequence = transactional(requiresNew)(fSequence)

    private val segmentSize = sequence.step
    private var hi = sequence.nextValue(1)
    private var low = n.zero

    require(segmentSize > 1,
        "The sequence step must be greater than one. " +
            "The step is used as the segment size for the id generation.")

    @tailrec final def nextId: E#ID =
        synchronized {
            if (n.lteq(low, n.fromInt(segmentSize)))
                n.plus(hi, low)
            else {
                hi = sequence.nextValue(segmentSize)
                nextId
            }
        }

}

abstract class SequencedIdGenerator[E <: BaseEntity: Manifest](
    val sequence: Sequence[E#ID])
        extends IdGenerator[E] {
    def nextId =
        sequence.nextValue(1)
}
