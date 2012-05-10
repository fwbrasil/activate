package net.fwbrasil.activate.util.uuid

import com.fasterxml.uuid.Generators
import java.util.{ UUID => JavaUUID }

object UUIDUtil {

	private val uuidGenerator = Generators.timeBasedGenerator
	def generateUUID = uuidGenerator.generate.toString
	def timestamp(uuid: String) = (JavaUUID.fromString(uuid).timestamp() - 122192928000000000l) / 10000

}
