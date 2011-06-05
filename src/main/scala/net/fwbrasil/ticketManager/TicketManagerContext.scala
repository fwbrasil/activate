package net.fwbrasil.ticketManager

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.memory.PrevaylerMemoryStorage
import net.fwbrasil.activate.storage.memory.MemoryStorage
import net.fwbrasil.activate.storage.relational.SimpleJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational._
import net.fwbrasil.activate.serialization._

object TicketManagerContext extends ActivateContext {
	
	def contextName = "testPrevaylerMemoryStorage"
	val storage = new SimpleJdbcRelationalStorage {
		val jdbcDriver = "com.mysql.jdbc.Driver"
		val user = "root"
		val password = "root"
		val url = "jdbc:mysql://127.0.0.1/teste?user=root&password=root"
		val dialect = mySqlDialect
		val serializator = javaSerializator
	} 
		
}