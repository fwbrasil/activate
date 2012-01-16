package net.fwbrasil.activate.util.uuid

import java.util.Date

trait Idable {

	val id = UUIDUtil generateUUID
	def creationTimestamp = UUIDUtil timestamp id
	def creationDate = new Date(creationTimestamp)

}
