package net.fwbrasil.activate.serialization

import net.fwbrasil.scala.UnsafeLazy._
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.SerializableEntityValue
import net.fwbrasil.activate.util.ManifestUtil._

trait SerializationContext {

    protected class Serialize[E <: BaseEntity: Manifest](columns: Set[String]) {
        def using(serializer: Serializer) = {
            var map = Map[(Class[_ <: BaseEntity], String), Serializer]()
            for (column <- columns)
                map += (erasureOf[E], column) -> serializer
            map
        }
    }

    protected def serialize[E <: BaseEntity: Manifest](f: (E => Unit)*) = {
        val mock = StatementMocks.mockEntity(erasureOf[E])
        f.foreach(_(mock))
        val vars = StatementMocks.fakeVarCalledStack.toSet
        val invalid = vars.filter(!_.baseTVal(None).isInstanceOf[SerializableEntityValue[_]])
        if (invalid.nonEmpty)
            throw new IllegalArgumentException(
                "Triyng to define a custom serializer for a supported property type. " +
                    "Class " + erasureOf[E].getSimpleName + " - properties: " + invalid.map(_.name).mkString(", "))
        new Serialize[E](vars.map(_.name))
    }

    def serializerFor(clazz: Class[_ <: BaseEntity], property: String) =
        customSerializerMap.getOrElse((clazz, property), defaultSerializer)

    protected val defaultSerializer: Serializer = jsonSerializer

    private val customSerializerMap =
        unsafeLazy(customSerializers.flatten.toMap)

    protected def customSerializers = List[Map[(Class[_ <: BaseEntity], String), Serializer]]()

}