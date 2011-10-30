package net.fwbrasil.activate.util.uuid

import _root_.org.safehaus.uuid.{UUIDGenerator, UUID => JugUUID}
import _root_.java.util.{UUID => JavaUUID}

object UUIDUtil {

	private def uuidGenerator = UUIDGenerator.getInstance
	def generateUUID = uuidGenerator.generateTimeBasedUUID().toString
	def timestamp(uuid: String) = (JavaUUID.fromString(uuid).timestamp() - 122192928000000000l) /10000

}
