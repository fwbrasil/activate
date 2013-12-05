package net.fwbrasil.activate.sequence

import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.id.UUID

abstract class SequenceEntity[T] private[activate] (
    val name: String,
    val step: Int)(
        implicit ctx: ActivateContext)
        extends Sequence[T]
        with BaseEntity
        with UUID

class IntSequenceEntity private[activate] (
    name: String,
    step: Int)(
        implicit ctx: ActivateContext)
        extends SequenceEntity[Int](name, step) {

    private var value = 0

    def _nextValue = {
        value += step
        value
    }
}

object IntSequenceEntity {
    def apply(sequenceName: String, step: Int = 1)(implicit ctx: ActivateContext) = {
        import ctx._
        transactional(requiresNew) {
            select[IntSequenceEntity].where(_.name :== sequenceName).headOption.getOrElse {
                new IntSequenceEntity(sequenceName, step)
            }
        }
    }
}

class LongSequenceEntity private[activate] (
    name: String,
    step: Int)(
        implicit ctx: ActivateContext)
        extends SequenceEntity[Long](name, step) {

    private var value = 0l

    def _nextValue = {
        value = value + step.toLong
        value
    }
}

object LongSequenceEntity {
    def apply(sequenceName: String, step: Int = 1)(implicit ctx: ActivateContext) = {
        import ctx._
        select[LongSequenceEntity].where(_.name :== sequenceName).headOption.getOrElse {
            new LongSequenceEntity(sequenceName, step)
        }
    }
}

