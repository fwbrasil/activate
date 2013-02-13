package net.fwbrasil.activate

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.Storage

object polyglotContext extends PolyglotActivateContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.postgresql.Driver"
        val user = "postgres"
        val password = "postgres"
        val url = "jdbc:postgresql://127.0.0.1/activate_test"
        val dialect = postgresqlDialect
    }
    val mysqlStorage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "com.mysql.jdbc.Driver"
        val user = "root"
        val password = ""
        val url = "jdbc:mysql://127.0.0.1/activate_test"
        val dialect = mySqlDialect
    }
    override val additionalStorages = Map[Storage[_], Set[Class[Entity]]](mysqlStorage -> Set(entity[MysqlEntity]))
}
import polyglotContext._

class CreateSchemaMigration extends Migration {
    def timestamp = System.currentTimeMillis
    def up = {
        removeAllEntitiesTables.ifExists
        createTableForAllEntities
    }
}

class PostgreEntity(var string: String) extends Entity
class MysqlEntity(var string: String) extends Entity

@RunWith(classOf[JUnitRunner])
class PolyglotPersistenceSpecs extends SpecificationWithJUnit {
    "Polyglot persistence" should {
        "work" in {
            transactional {
                new PostgreEntity("a")
                new MysqlEntity("b")
            }
            transactional {
                all[PostgreEntity].head.string = "c"
                all[MysqlEntity].head.string = "d"
                println("a")
            }
            ok
        }
    }
}