package net.fwbrasil.activate.coordinator

import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.ActivateContext

import net.fwbrasil.activate.migration.Migration

object coordinatorTestContext extends ActivateContext {
	val storage = new PooledJdbcRelationalStorage {
		val jdbcDriver = "org.postgresql.Driver"
		val user = "postgres"
		val password = ""
		val url = "jdbc:postgresql://127.0.0.1/activate_test"
		val dialect = postgresqlDialect
	}
}

import coordinatorTestContext._

class CreateTables extends Migration {

	val timestamp = 1l

	def up = {
		createTableForAllEntities.ifNotExists
	}
}

class SomeEntity(var integer: Int) extends Entity

case class Runner(entityId: String, numOfVMs: Int, numOfThreads: Int, numOfTransactions: Int) {
	def run = {
		val tasks =
			for (i <- 0 until numOfVMs)
				yield fork
		tasks.map(_.execute)
		tasks.map(_.join)
	}
	def fork =
		JvmFork.fork(128, 1024, Some("-Dactivate.coordinator.serverHost=localhost")) {
			runThreads
		}
	def runThreads = {
		val threads =
			for (i <- 0 until numOfThreads)
				yield new Thread {
				override def run =
					for (i <- 0 until numOfTransactions)
						transactional {
							byId[SomeEntity](entityId).get.integer += 1
						}
			}
		threads.map(_.start)
		threads.map(_.join)
	}
}

object Teste extends App {

	val numOfVMs = 2
	val numOfThreads = 2
	val numOfTransactions = 100

	val entityId =
		transactional {
			new SomeEntity(0).id
		}

	Runner(entityId, numOfVMs, numOfThreads, numOfTransactions).run

	val i = transactional {
		byId[SomeEntity](entityId).get.integer
	}
	println(i)
	require(i == numOfVMs * numOfThreads * numOfTransactions)
}