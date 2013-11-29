package net.fwbrasil.activate.lift

import net.fwbrasil.activate.entity.map.EntityMap
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.ActivateContext
import net.liftweb.util.FieldError
import net.fwbrasil.activate.entity.InvariantViolationException
import net.liftweb.http.S
import net.liftweb.util.FieldIdentifier
import net.liftweb.common.Box
import net.fwbrasil.activate.entity.map.EntityMapBase
import net.fwbrasil.activate.entity.map.MutableEntityMap

class MutableEntityForm[E <: BaseEntity] private[activate] (values: Map[String, Any])(implicit m: Manifest[E], context: ActivateContext) extends MutableEntityMap[E](values) {

    def this(entity: E)(implicit m: Manifest[E], context: ActivateContext) =
        this(entity.vars.map(ref => (ref.name, EntityMapBase.varToValue(ref))).toMap)

    def this(init: ((E) => (_, _))*)(implicit m: Manifest[E], context: ActivateContext) =
        this(init.map(EntityMapBase.keyAndValueFor[E](_)(m)).toMap)

    override def createEntity =
        EntityForm.translateInvariantsExceptions {
            super.createEntity
        }

    override def updateEntity(entity: E, values: Map[String, Any]) =
        EntityForm.translateInvariantsExceptions {
            super.updateEntity(entity, values)
        }

}