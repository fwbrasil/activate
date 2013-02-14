//package net.fwbrasil.activate
//
//import org.specs2.mutable._
//import org.junit.runner._
//import org.specs2.runner._
//import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
//import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
//import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
//import net.fwbrasil.activate.storage.Storage
//
//object polyglotContext extends ActivateContext {
//    val storage = new PooledJdbcRelationalStorage {
//        val jdbcDriver = "org.postgresql.Driver"
//        val user = "postgres"
//        val password = "postgres"
//        val url = "jdbc:postgresql://127.0.0.1/activate_test"
//        val dialect = postgresqlDialect
//    }
//    val mysqlStorage = new PooledJdbcRelationalStorage {
//        val jdbcDriver = "com.mysql.jdbc.Driver"
//        val user = "root"
//        val password = ""
//        val url = "jdbc:mysql://127.0.0.1/activate_test"
//        val dialect = mySqlDialect
//    }
//    override def additionalStorages =
//        Map(mysqlStorage -> Set(classOf[MysqlEntity]))
//}
//import polyglotContext._
//
//class CreateSchemaMigration extends Migration {
//    def timestamp = System.currentTimeMillis
//    def up = {
//        removeReferencesForAllEntities.ifExists
//        removeAllEntitiesTables.ifExists
//        createTableForAllEntities
//        createReferencesForAllEntities
//    }
//}
//
//class PostgreEntity(var string: String, var mysqlEntity: Option[MysqlEntity] = None) extends Entity
//class MysqlEntity(var string: String, var postgreEntity: Option[PostgreEntity] = None) extends Entity
//
//@RunWith(classOf[JUnitRunner])
//class PolyglotPersistenceSpecs extends ActivateTest {
//    "Polyglot persistence" should {
//        "work" in {
//            activateTest(
//                (step: StepExecutor) => {
//                    import step.ctx._
//                    val (postgreEntityId, musqlEntityId) =
//                        step {
//                            (new PostgreEntity("a").id, new MysqlEntity("b").id)
//                        }
//                    def postgreEntity = byId[PostgreEntity](postgreEntityId).get
//                    def musqlEntity = byId[PostgreEntity](musqlEntityId).get
//                    step {
//                        postgreEntity.string = "c"
//                        musqlEntity.string = "d"
//                    }
//                })
//        }
//    }
//}