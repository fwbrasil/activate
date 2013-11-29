package net.fwbrasil.activate.entity.map

import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.ActivateContext

class MutableEntityMap[E <: BaseEntity] private[activate] (var _values: Map[String, Any])(
    implicit val m: Manifest[E], val context: ActivateContext)
        extends EntityMapBase[E, MutableEntityMap[E]] {

    verifyValuesTypes

    def this(entity: E)(implicit m: Manifest[E], context: ActivateContext) =
        this(entity.vars.map(ref =>
            (ref.name, EntityMapBase.varToValue(ref))).toMap)

    def this(init: ((E) => (_, _))*)(implicit m: Manifest[E], context: ActivateContext) =
        this(init.map(EntityMapBase.keyAndValueFor[E](_)(m)).toMap)

    def put[V, V1 <: V](f: E => V)(value: V1) = {
        _values += EntityMapBase.keyFor(f) -> value
        this
    }

    def values = _values

}