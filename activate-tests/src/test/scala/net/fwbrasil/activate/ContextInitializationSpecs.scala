package net.fwbrasil.activate

import net.fwbrasil.activate.storage.memory.MemoryStorage
//import net.fwbrasil.activate.entity.Entity

object contextInitializationSpecsContext extends ActivateContext {
	def contextName = "contextInitializationSpecsContext"
	val storage = new MemoryStorage
}

import contextInitializationSpecsContext._
class TestEntity(var string: String) extends Entity

object ContextInitializationSpecs extends App {

	println(new TestEntity("felipe"))
	println(transactional(all[TestEntity]))
}