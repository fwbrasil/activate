//package net.fwbrasil.activate.multipleVms
//
//import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
//import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
//import net.fwbrasil.activate.ActivateContext
//import net.fwbrasil.activate.migration.Migration
//import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
//import scala.actors.remote.NetKernel
//import net.fwbrasil.activate.ActivateTest
//import org.junit.runner.RunWith
//import org.specs2.runner.JUnitRunner
//import ctx1._
//
//@RunWith(classOf[JUnitRunner])
//class MultiVMSpecs extends ActivateTest {
//
//    val coordinatorClientOption = "-Dactivate.coordinator.serverHost=localhost"
//    val optimisticOfflineLockingOption = "-Dactivate.coordinator.optimisticOfflineLocking.enable=true"
//    val optimisticOfflineLockingValidateReadOption = "-Dactivate.coordinator.optimisticOfflineLocking.validateReads=true"
//
//    "Multiple VMs" should {
//        "work with coordinator" in synchronized {
//            test(mainVmOptions = List(),
//                forkVmOptions = List(coordinatorClientOption),
//                expectSucess = true)
//        }
//        "work with offline locking with read validation" in synchronized {
//            test(mainVmOptions = List(optimisticOfflineLockingOption, optimisticOfflineLockingValidateReadOption),
//                forkVmOptions = List(optimisticOfflineLockingOption),
//                expectSucess = true)
//        }
//        "work with offline locking + coordinator" in synchronized {
//            test(mainVmOptions = List(optimisticOfflineLockingOption),
//                forkVmOptions = List(optimisticOfflineLockingOption, coordinatorClientOption),
//                expectSucess = true)
//        }
//        "not work without coordinator and offline locking" in synchronized {
//            test(mainVmOptions = List(),
//                forkVmOptions = List(),
//                expectSucess = false)
//        }
//        "not work with optimistic offline locking without read validation" in synchronized {
//            test(mainVmOptions = List(optimisticOfflineLockingOption),
//                forkVmOptions = List(optimisticOfflineLockingOption),
//                expectSucess = false)
//        }
//    }
//
//    private def test(mainVmOptions: List[String], forkVmOptions: List[String], expectSucess: Boolean) = {
//        val numOfVMs = 2
//        val numOfThreads = 4
//        val numOfTransactions = 30
//        val result =
//            MainVM(numOfVMs, numOfThreads, numOfTransactions, mainVmOptions, forkVmOptions).result
//        if (expectSucess)
//            result mustEqual (numOfVMs * numOfThreads * numOfTransactions)
//        else
//            result mustNotEqual (numOfVMs * numOfThreads * numOfTransactions)
//    }
//
//}
//
//case class MainVM(
//        numOfVMs: Int,
//        numOfThreads: Int,
//        numOfTransactions: Int,
//        mainVmOptions: List[String],
//        forkVmOptions: List[String]) {
//
//    val result =
//        JvmFork.forkAndExpect(others = mainVmOptions) {
//            start
//            val entityId =
//                transactional {
//                    (new IntEntity).id
//                }
//
//            val forks =
//                for (i <- 0 until numOfVMs) yield JvmFork.fork(others = forkVmOptions) {
//                    ForkVM(entityId, numOfThreads, numOfTransactions)
//                }
//            forks.map(_.execute)
//            forks.map(_.join)
//
//            transactional {
//                val entity = byId[IntEntity](entityId).get
//                entity.intValue
//            }
//        }
//
//}
//
//case class ForkVM(entityId: String, numOfThreads: Int, numOfTransactions: Int) {
//    {
//        start
//
//        val threads =
//            for (i <- 0 until numOfThreads)
//                yield new Thread {
//                override def run =
//                    for (i <- 0 until numOfTransactions)
//                        transactional {
//                            byId[IntEntity](entityId).get.intValue += 1
//                        }
//            }
//        threads.map(_.start)
//        threads.map(_.join)
//    }
//}