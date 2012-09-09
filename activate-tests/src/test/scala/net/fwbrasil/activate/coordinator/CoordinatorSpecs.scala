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

class CoordinatorTestContext extends ActivateTestContext {
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
		ActivateContext.clearContextCache
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

	"Test" should {
		"work!" in {
			val (entityCtx1, entityId) =
				ctx1.run {
					import ctx1._
					val entity = newFullActivateTestEntity
					entity.intValue = 1
					(entity, entity.id)
				}
			val entityCtx2 = ctx2.run {
				import ctx2._
				byId[ActivateTestEntity](entityId).get
			}
			entityCtx1 mustNotEqual (entityCtx2)
			ctx2.run {
				import ctx2._
				entityCtx2.intValue += 1
			}
			ctx1.run {
				import ctx1._
				entityCtx1.intValue += 1
				entityCtx1.intValue
			} mustEqual 3
			ok
		}
	}

}