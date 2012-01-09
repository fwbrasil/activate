package net.fwbrasil.thor

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.memory.MemoryStorage
import net.fwbrasil.activate.storage.relational.SimpleJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.mySqlDialect
import net.fwbrasil.activate.serialization.javaSerializator

object thorContext extends ActivateContext {
	//	val storage = new MemoryStorage {}
	val storage = new SimpleJdbcRelationalStorage {
		val jdbcDriver = "com.mysql.jdbc.Driver"
		val user = "root"
		val password = ""
		val url = "jdbc:mysql://127.0.0.1/crud"
		val dialect = mySqlDialect
		val serializator = javaSerializator
	}
	def contextName = "thorContext"
}