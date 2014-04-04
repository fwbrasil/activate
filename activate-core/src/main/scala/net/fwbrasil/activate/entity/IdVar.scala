package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.uuid.UUIDUtil

class IdVar private (metadata: EntityPropertyMetadata, pOuterEntity: BaseEntity, var entityIdOption: Option[BaseEntity#ID])
        extends Var[BaseEntity#ID](metadata, pOuterEntity, false) {

    def this(metadata: EntityPropertyMetadata, outerEntity: BaseEntity) =
        this(metadata, outerEntity, None)

    def this(metadata: EntityPropertyMetadata, outerEntity: BaseEntity, entityId: BaseEntity#ID) =
        this(metadata, outerEntity, Some(entityId))

    if (entityIdOption.isDefined)
        super.put(entityIdOption)

    override def getValue() =
        entityIdOption.getOrElse(null).asInstanceOf[BaseEntity#ID]

    override def get =
        entityIdOption

    override def put(value: Option[BaseEntity#ID]) =
        putWithoutInitialize(value)
    
    override def putWithoutInitialize(value: Option[BaseEntity#ID]) =
        if (value.isDefined) {
            entityIdOption = value
            super.putWithoutInitialize(value)
        }

    override protected def doInitialized[A](forWrite: Boolean)(f: => A): A =
        f

    override def isDestroyed: Boolean = {
        if (outerEntity != null) outerEntity.initialize(false, false)
        super.isDestroyed
    }
    
    override def isMutable = false 

    override def toString = "id -> " + getValue
}