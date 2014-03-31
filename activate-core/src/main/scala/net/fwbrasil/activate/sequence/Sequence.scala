package net.fwbrasil.activate.sequence

import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.ActivateContext

abstract class Sequence[T] {

    private[activate] def context: ActivateContext

    val step: Int

    final def nextValue =
        synchronized {
            context.transactional(context.requiresNew) {
                _nextValue
            }
        }

    protected def _nextValue: T
}