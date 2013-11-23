package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.uuid.UUIDUtil

class IdVar(metadata: EntityPropertyMetadata, outerEntity: Entity, val entityId: Entity#ID)
        extends Var[Entity#ID](metadata, outerEntity, false) {

    def this(metadata: EntityPropertyMetadata, outerEntity: Entity) =
        this(metadata, outerEntity, outerEntity.context.nextIdFor(outerEntity.getClass))

    super.putValue(entityId)

    override def getValue =
        entityId
    
    override def get =
        Some(entityId)

    override protected def doInitialized[A](forWrite: Boolean)(f: => A): A =
        f

    override def isDestroyed: Boolean = {
        if (outerEntity != null) outerEntity.initialize(false)
        super.isDestroyed
    }

    override def toString = "id -> " + entityId
}