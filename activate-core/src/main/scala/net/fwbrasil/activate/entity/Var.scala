package net.fwbrasil.activate.entity

import language.existentials
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.serialization.SerializationContext

class Var[T](
    val name: String,
    val isTransient: Boolean,
    val baseTVal: Option[Any] => EntityValue[Any],
    val valueClass: Class[_],
    val isLazyFlag: Boolean,
    val outerEntity: Entity,
    initialize: Boolean,
    valueOption: Option[T])
        extends Ref[T](valueOption, initialize)(outerEntity.context)
        with java.io.Serializable {

    def this(metadata: EntityPropertyMetadata, outerEntity: Entity, initialize: Boolean) =
        this(
            metadata.name,
            metadata.isTransient,
            metadata.tval,
            metadata.propertyType,
            metadata.isLazyFlag,
            outerEntity,
            initialize || metadata.isLazyFlag,
            if (metadata.isLazyFlag)
                Some(false.asInstanceOf[T])
            else
                None)

    val tval = {
        if (baseTVal == null)
            null
        else {
            val empty = baseTVal(None)
            (empty match {
                case v: SerializableEntityValue[_] =>
                    (value: Option[T]) =>
                        baseTVal(value).asInstanceOf[SerializableEntityValue[_]].forSerializator {
                            context.asInstanceOf[SerializationContext].serializerFor(outerEntityClass, name)
                        }
                        case other =>
                    baseTVal
            }).asInstanceOf[Option[T] => EntityValue[T]]
        }
    }

    var initialized = false

    def toEntityPropertyValue(value: T) = tval(Option(value))
    def outerEntityClass = outerEntity.niceClass

    override def get =
        doInitialized(forWrite = false) {
            if (outerEntity == null)
                throw new IllegalStateException("Var isnt bound to an Entity.")
            super.get
        }

    // Better performance than use Source.!
    def getValue() =
        get.getOrElse(null.asInstanceOf[T])
    def putValue(value: T) =
        put(Option(value))

    override def put(value: Option[T]): Unit =
        doInitialized(forWrite = true) {
            super.put(value)
        }

    def getValueWithoutInitialize() =
        super.get.getOrElse(null.asInstanceOf[T])

    def putWithoutInitialize(value: Option[T]) =
        super.put(value)

    def putValueWithoutInitialize(value: T) =
        putWithoutInitialize(Option(value))

    override def destroy: Unit =
        doInitialized(forWrite = true) {
            super.destroy
        }

    override def isDestroyed: Boolean =
        doInitialized(forWrite = false) {
            super.isDestroyed
        }

    private[activate] def isDestroyedSnapshot: Boolean = {
        super.isDestroyed
    }

    protected def doInitialized[A](forWrite: Boolean)(f: => A): A = {
        if (outerEntity != null) outerEntity.initialize(forWrite)
        f
    }

    protected def writeReplace(): AnyRef = {
        if (isTransient)
            setRefContent(None)
        this
    }

    protected def readResolve(): Any = {
        if (isTransient)
            outerEntity.entityMetadata.propertiesMetadata
                .find(_.name == name).get.varField
                .set(outerEntity, this)
        this
    }

    private[activate] def snapshotWithoutTransaction =
        super.snapshot

    override def toString = name + " -> " + get.getOrElse("")
}

