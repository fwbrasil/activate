package net.fwbrasil.activate.entity.map

import scala.language._
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import scala.concurrent.Future
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.EntityValidation
import net.fwbrasil.activate.entity.IdVar
import net.fwbrasil.activate.entity.Var

class EntityMap[E <: BaseEntity] private[activate] (val _values: Map[String, Any])(
    implicit val m: Manifest[E], val context: ActivateContext)
        extends EntityMapBase[E, EntityMap[E]] {
    
    verifyValuesTypes

    def this(entity: E)(implicit m: Manifest[E], context: ActivateContext) =
        this(entity.vars.map(ref =>
            (ref.name, EntityMapBase.varToValue(ref))).toMap)

    def this(init: ((E) => (_, _))*)(implicit m: Manifest[E], context: ActivateContext) =
        this(init.map(EntityMapBase.keyAndValueFor[E](_)(m)).toMap)

    def put[V, V1 <: V](f: E => V)(value: V1) =
        new EntityMap[E](values ++ List(EntityMapBase.keyFor(f) -> value))
        
    def values = _values

}

