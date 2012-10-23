package net.fwbrasil.activate.coordinator

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.idiom.h2Dialect
import net.fwbrasil.activate.storage.relational.SimpleJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import scala.actors.remote.NetKernel
import scala.actors.remote._
import scala.actors.remote.RemoteActor._
class CoordinatorTestContext extends ActivateTestContext {

	System.setProperty("activate.coordinator.server", "true")
	protected override lazy val coordinatorClientOption =
		if (storage.isMemoryStorage)
			None
		else
			Coordinator.clientOption(this)

	val storage = new PooledJdbcRelationalStorage {
		val jdbcDriver = "org.postgresql.Driver"
		val user = "postgres"
		val password = ""
		val url = "jdbc:postgresql://127.0.0.1/activate_test"
		val dialect = postgresqlDialect
	}
	stop
	def run[A](f: => A) = {
		start
		try
			transactional(f)
		finally
			stop
	}

}

object ctx1 extends CoordinatorTestContext
object ctx2 extends CoordinatorTestContext

@RunWith(classOf[JUnitRunner])
class CoordinatorSpecs extends ActivateTest {

	"Coordinator" should {
		"detect concurrent modification" in synchronized {
			val (entityCtx1, entityCtx2) = prepareEntity
			ctx2.run {
				import ctx2._
				entityCtx2.intValue += 1
			}
			ctx1.run {
				import ctx1._
				entityCtx1.intValue += 1
			}
			ctx1.run {
				import ctx1._
				entityCtx1.intValue
			} mustEqual 3
		}
		"detect concurrent delete (read)" in synchronized {
			val (entityCtx1, entityCtx2) = prepareEntity
			ctx2.run {
				import ctx2._
				entityCtx2.delete
			}
			(ctx1.run {
				import ctx1._
				entityCtx1.intValue
			}) must throwA[IllegalStateException]
		}
		"detect concurrent delete (read)" in synchronized {
			val (entityCtx1, entityCtx2) = prepareEntity
			ctx2.run {
				import ctx2._
				entityCtx2.delete
			}
			(ctx1.run {
				import ctx1._
				entityCtx1.intValue += 1
			}) must throwA[IllegalStateException]
		}
		"timeout while connecting to the server" in {
			val remoteActor = select(Node("199.9.9.9", 9999), Coordinator.actorName)
			(new CoordinatorClient(ctx2, remoteActor)) must throwA[IllegalStateException]
		}
	}

	private def prepareEntity = {
		ctx1.contextName
		ctx2.contextName
		val (entityCtx1, entityId) =
			ctx1.run {
				import ctx1._
				val entity = newEmptyActivateTestEntity
				entity.intValue = 1
				(entity, entity.id)
			}
		val entityCtx2 = ctx2.run {
			import ctx2._
			byId[ActivateTestEntity](entityId).get
		}
		entityCtx1 mustNotEqual (entityCtx2)
		(entityCtx1, entityCtx2)
	}

}