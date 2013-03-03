package net.fwbrasil.activate.coordinator

import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import multiVMTestContext._
import scala.actors.remote.NetKernel
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

object multiVMTestContext extends CoordinatorTestContext

case class Runner(entityId: String, numOfVMs: Int, numOfThreads: Int, numOfTransactions: Int) {
    def run = {
        val tasks =
            for (i <- 0 until numOfVMs)
                yield fork(false)
        tasks.map(_.execute)
        tasks.map(_.join)
    }
    def fork(server: Boolean) =
        JvmFork.fork(128, 1024, None /*Some("-Dactivate.coordinator.serverHost=localhost")*/ ) {
            multiVMTestContext.start
            runThreads
        }
    def runThreads = {
        val threads =
            for (i <- 0 until numOfThreads)
                yield new Thread {
                override def run =
                    for (i <- 0 until numOfTransactions)
                        multiVMTestContext.transactional {
                            byId[IntEntity](entityId).get.intValue += 1
                        }
            }
        threads.map(_.start)
        threads.map(_.join)
    }
}

@RunWith(classOf[JUnitRunner])
class CoordinatorMultiVMSpecs extends ActivateTest {

    "Coordinator with multiple VMs" should {
        "work :)" in {
            val numOfVMs = 2
            val numOfThreads = 4
            val numOfTransactions = 30

            start

            val entityId =
                transactional {
                    (new IntEntity).id
                }

            Runner(entityId, numOfVMs, numOfThreads, numOfTransactions).run

            val i = transactional {
                val entity = byId[IntEntity](entityId).get
                entity.intValue
            }

            stop
            i mustEqual (numOfVMs * numOfThreads * numOfTransactions)
        }
    }

}