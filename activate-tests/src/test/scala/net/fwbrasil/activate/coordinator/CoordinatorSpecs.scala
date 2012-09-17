//package net.fwbrasil.activate.coordinator
//
//import org.specs2.mutable._
//import org.junit.runner._
//import org.specs2.runner._
//import net.fwbrasil.activate.ActivateContext
//import net.fwbrasil.activate.storage.relational.idiom.h2Dialect
//import net.fwbrasil.activate.storage.relational.SimpleJdbcRelationalStorage
//import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
//import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
//import net.fwbrasil.activate.ActivateTestContext
//import net.fwbrasil.activate.ActivateTest
//import net.fwbrasil.activate.util.RichList._
//import net.fwbrasil.activate.JvmFork
//
//class CoordinatorTestContext extends ActivateTestContext {
//	val storage = new PooledJdbcRelationalStorage {
//		val jdbcDriver = "org.postgresql.Driver"
//		val user = "postgres"
//		val password = ""
//		val url = "jdbc:postgresql://127.0.0.1/activate_test"
//		val dialect = postgresqlDialect
//	}
//	stop
//	def run[A](f: => A) = {
//		start
//		try
//			transactional(f)
//		finally
//			stop
//	}
//}
//
//object ctx1 extends CoordinatorTestContext
//object ctx2 extends CoordinatorTestContext
//object ctx3 extends CoordinatorTestContext
//object ctx4 extends CoordinatorTestContext
//
//object ConcurrencyTest extends App {
//
//	ActivateContext.useContextCache = false
//
//	val ctxs = List(ctx1, ctx2, ctx3) //, ctx4)
//	val entityId =
//		ctx1.run {
//			ctx1.newEmptyActivateTestEntity.id
//		}
//
//	for (ctx <- ctxs)
//		ctx.run {
//			ctx.byId[ctx.ActivateTestEntity](entityId).get.intValue
//		}
//
//	class Add(ctx: CoordinatorTestContext) extends Thread {
//		override def run =
//			ctx.transactional {
//				ctx.byId[ctx.ActivateTestEntity](entityId).get.intValue += 1
//			}
//	}
//
//	val threads = ctxs.map(ctx => (0 until 50).map(_ => new Add(ctx))).flatten
//	threads.randomize.foreach(_.start)
//	threads.foreach(_.join)
//
//	import ctx2._
//	val i = transactional {
//		byId[ActivateTestEntity](entityId).get.intValue
//	}
//	println(i)
//	require(i == threads.size)
//}
//
//object MultiVMConcurrencyTest extends App {
//
//	import ctx1._
//	start
//
//	val entityId =
//		transactional {
//			newEmptyActivateTestEntity.id
//		}
//
//	//	val tasks =
//	//		(0 until 4).map { i =>
//	//			JvmFork.fork(128, 1024, Some("")) {
//	//				start
//	//				for (i <- 0 until 10)
//	//					byId[ActivateTestEntity](entityId).get.intValue += 1
//	//			}
//	//		}
//	//
//	//	tasks.foreach(_.execute)
//	//	tasks.foreach(_.join)
//	val a = JvmFork.fork(128, 1024, Some("")) {
//		//		start
//		//		for (i <- 0 until 10)
//		//			byId[ActivateTestEntity](entityId).get.intValue += 1
//		//		1
//		println("a")
//	}
//	val i = transactional {
//		byId[ActivateTestEntity](entityId).get.intValue
//	}
//	println(i)
//}
//
//@RunWith(classOf[JUnitRunner])
//class CoordinatorSpecs extends ActivateTest {
//
//	"Test" should {
//		"work!" in {
//			val (entityCtx1, entityId) =
//				ctx1.run {
//					import ctx1._
//					val entity = newEmptyActivateTestEntity
//					entity.intValue = 1
//					(entity, entity.id)
//				}
//			val entityCtx2 = ctx2.run {
//				import ctx2._
//				byId[ActivateTestEntity](entityId).get
//			}
//			entityCtx1 mustNotEqual (entityCtx2)
//			ctx2.run {
//				import ctx2._
//				entityCtx2.intValue += 1
//			}
//			ctx1.run {
//				import ctx1._
//				entityCtx1.intValue += 1
//				entityCtx1.intValue
//			} mustEqual 3
//		}
//	}
//
//}