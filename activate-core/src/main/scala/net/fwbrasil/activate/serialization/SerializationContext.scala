package net.fwbrasil.activate.serialization

import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.SerializableEntityValue
import net.fwbrasil.activate.util.ManifestUtil._

trait SerializationContext {

	protected class Serialize[E <: Entity: Manifest](columns: Set[String]) {
		def using(serializer: Serializator) = {
			var map = Map[(Class[_ <: Entity], String), Serializator]()
			for (column <- columns)
				map += (erasureOf[E], column) -> serializer
			map
		}
	}

	protected def serialize[E <: Entity: Manifest](f: (E => Unit)*) = {
		val mock = StatementMocks.mockEntity(erasureOf[E])
		f.foreach(_(mock))
		val vars = StatementMocks.fakeVarCalledStack.toSet
		val invalid = vars.filter(!_.baseTVal(None).isInstanceOf[SerializableEntityValue[_]])
		if (invalid.nonEmpty)
			throw new IllegalArgumentException(
				"Triyng to define a custom serializator for a supported property type. " +
					"Class " + erasureOf[E].getSimpleName + " - properties: " + invalid.map(_.name).mkString(", "))
		new Serialize[E](vars.map(_.name))
	}

	private[activate] def serializatorFor(clazz: Class[_ <: Entity], property: String) =
		customSerializatorsMap.getOrElse((clazz, property), defaultSerializator)

	protected lazy val defaultSerializator: Serializator = jsonSerializator
	protected lazy val customSerializators = List[Map[(Class[_ <: Entity], String), Serializator]]()

	private lazy val customSerializatorsMap =
		customSerializators.flatten.toMap

}