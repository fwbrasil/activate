package net.fwbrasil.activate.coordinator

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.idiom.h2Dialect
import net.fwbrasil.activate.storage.relational.SimpleJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import scala.actors.remote.NetKernel
import scala.actors.remote._
import scala.actors.remote.RemoteActor._
import net.fwbrasil.activate.mongoContext
import net.fwbrasil.activate.StoppableActivateContext
import net.fwbrasil.activate.mysqlContext

class CoordinatorTestContext extends StoppableActivateContext {

    Coordinator.timeout = 1000

    class IntEntity extends Entity {
        var intValue = 0
    }

    val storage = mongoContext.storage

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

    //    System.setProperty("activate.coordinator.server", "true")

    "Coordinator" should {
        "detect concurrent modification (write)" in synchronized {
            val (entityCtx1, entityCtx2) = prepareEntity
            ctx2.run {
                import ctx2._
                entityCtx2.intValue = 3
            }
            ctx1.run {
                import ctx1._
                entityCtx1.intValue
            } mustEqual 3
        }
        "detect concurrent modification (read+write)" in synchronized {
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
        "detect concurrent delete (read/write)" in synchronized {
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
        "timeout while connecting to the server" in synchronized {
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
                val entity = new IntEntity
                entity.intValue = 1
                (entity, entity.id)
            }
        val entityCtx2 = ctx2.run {
            import ctx2._
            byId[IntEntity](entityId).get
        }
        entityCtx1 mustNotEqual (entityCtx2)
        (entityCtx1, entityCtx2)
    }

}