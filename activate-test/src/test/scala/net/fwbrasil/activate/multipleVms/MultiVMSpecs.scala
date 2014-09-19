package net.fwbrasil.activate.multipleVms

import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import scala.actors.remote.NetKernel
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import ctx1._
import net.fwbrasil.activate.postgresqlContext

@RunWith(classOf[JUnitRunner])
class MultiVMSpecs extends ActivateTest {

    val optimisticOfflineLockingOption = "-Dactivate.offlineLocking.enable=true"
    val optimisticOfflineLockingValidateReadOption = "-Dactivate.offlineLocking.validateReads=true"

    if (!isTravis) {

        "Multiple VMs" should {
            "work with offline locking with read validation" in synchronized {
                test(mainVmOptions = List(optimisticOfflineLockingOption, optimisticOfflineLockingValidateReadOption),
                    forkVmOptions = List(optimisticOfflineLockingOption),
                    expectSucess = true)
            }
            "not work without offline locking" in synchronized {
                test(mainVmOptions = List(),
                    forkVmOptions = List(),
                    expectSucess = false)
            }
            "not work with optimistic offline locking without read validation" in synchronized {
                test(mainVmOptions = List(optimisticOfflineLockingOption),
                    forkVmOptions = List(optimisticOfflineLockingOption),
                    expectSucess = false)
            }
        }
    }

    private def test(mainVmOptions: List[String], forkVmOptions: List[String], expectSucess: Boolean) =
        if (super.contexts.find(_ == postgresqlContext).isDefined) {
            val numOfVMs = 4
            val numOfThreads = 4
            val numOfTransactions = 30
            val result =
                MainVM(numOfVMs, numOfThreads, numOfTransactions, mainVmOptions, forkVmOptions).result
            if (expectSucess)
                result mustEqual (numOfVMs * numOfThreads * numOfTransactions)
            else
                result mustNotEqual (numOfVMs * numOfThreads * numOfTransactions)
        } else
            ok

}

case class MainVM(
    numOfVMs: Int,
    numOfThreads: Int,
    numOfTransactions: Int,
    mainVmOptions: List[String],
    forkVmOptions: List[String]) {

    val result =
        JvmFork.forkAndExpect(others = mainVmOptions) {
            start
            val entityId =
                transactional {
                    (new IntEntity).id
                }

            val forks =
                for (i <- 0 until numOfVMs) yield JvmFork.fork(others = forkVmOptions) {
                    ForkVM(entityId, numOfThreads, numOfTransactions)
                }
            forks.map(_.execute)
            forks.map(_.join)

            transactional {
                val entity = byId[IntEntity](entityId).get
                entity.intValue
            }
        }

}

case class ForkVM(entityId: String, numOfThreads: Int, numOfTransactions: Int) {
    {
        start

        val threads =
            for (i <- 0 until numOfThreads)
                yield new Thread {
                override def run =
                    for (i <- 0 until numOfTransactions) {
                        transactional {
                            val entity = byId[IntEntity](entityId).get
                            val oldValue = entity.intValue
                            val newValue = oldValue + 1
                            entity.intValue = newValue
                            (entity, newValue)
                        }
                    }
            }
        threads.map(_.start)
        threads.map(_.join)
    }
}