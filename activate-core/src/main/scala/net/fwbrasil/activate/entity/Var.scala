package net.fwbrasil.activate.entity

import language.existentials
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.Transaction
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.serialization.SerializationContext

class Var[T](metadata: EntityPropertyMetadata, _outerEntity: Entity, initialize: Boolean)
        extends Ref[T](None, initialize)(_outerEntity.context)
        with java.io.Serializable {

    val outerEntity = _outerEntity
    val name = metadata.name
    val isTransient = metadata.isTransient
    val baseTVal = metadata.tval
    lazy val tval = {
        val empty = baseTVal(None)
        (empty match {
            case v: SerializableEntityValue[_] =>
                val serializator =
                    context.asInstanceOf[SerializationContext].serializatorFor(outerEntityClass, name)
                (value: Option[T]) =>
                    baseTVal(value).asInstanceOf[SerializableEntityValue[_]].forSerializator(serializator)
                    case other =>
                baseTVal
        }).asInstanceOf[Option[T] => EntityValue[T]]
    }

    var initialized = false

    def toEntityPropertyValue(value: T) = tval(Option(value))
    def outerEntityClass = outerEntity.niceClass
    val valueClass = metadata.propertyType

    override def get = doInitialized {
        if (outerEntity == null)
            throw new IllegalStateException("Var isnt bound to an Entity.")
        super.get
    }

    // Better performance than use Source.!
    def getValue() =
        get.getOrElse(null.asInstanceOf[T])
    def putValue(value: T) =
        put(Option(value))

    override def put(value: Option[T]): Unit = doInitialized {
        super.put(value)
    }

    override def destroy: Unit = doInitialized {
        super.destroy
    }

    override def isDestroyed: Boolean = doInitialized {
        super.isDestroyed
    }

    private[activate] def isDestroyedSnapshot: Boolean = {
        super.isDestroyed
    }

    protected def doInitialized[A](f: => A): A = {
        if (outerEntity != null) outerEntity.initialize
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

    override def toString = name + " -> " + get.getOrElse("")
}

