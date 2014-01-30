package net.fwbrasil.activate

import net.fwbrasil.activate.sequence.IntSequenceEntity
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.Reflection._
import org.joda.time.DateTime
import net.fwbrasil.activate.migration.Migration
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.storage.memory.TransientMemoryStorage
import net.fwbrasil.activate.storage.mongo.MongoStorage
import net.fwbrasil.activate.storage.prevayler.PrevaylerStorage
import net.fwbrasil.activate.storage.relational.idiom.oracleDialect
import java.sql.Blob
import java.sql.Date
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.storage.relational.idiom.h2Dialect
import net.fwbrasil.activate.storage.relational.SimpleJdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.derbyDialect
import net.fwbrasil.activate.storage.relational.idiom.hsqldbDialect
import net.fwbrasil.activate.serialization.xmlSerializer
import net.fwbrasil.activate.serialization.jsonSerializer
import net.fwbrasil.activate.storage.relational.idiom.db2Dialect
import org.joda.time.DateMidnight
import net.fwbrasil.activate.entity.LazyList
import net.fwbrasil.activate.storage.relational.async.AsyncPostgreSQLStorage
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import org.jboss.netty.util.CharsetUtil
import com.github.mauricio.async.db.pool.ObjectFactory
import com.github.mauricio.async.db.Connection
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import com.github.mauricio.async.db.pool.PoolConfiguration
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import net.fwbrasil.activate.storage.mongo.async.AsyncMongoStorage
import net.fwbrasil.activate.storage.prevalent.PrevalentStorage
import java.io.File
import net.fwbrasil.activate.entity.EntityMetadata
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.util.ManifestUtil._
import scala.xml.Elem
import net.fwbrasil.activate.storage.relational.idiom.sqlServerDialect
import net.fwbrasil.activate.multipleVms._
import EnumerationValue._
import scala.util.Random
import net.fwbrasil.activate.cache.CacheType
import net.liftweb.http.js.JE.Num
import net.fwbrasil.activate.cache.CustomCache
import scala.collection.immutable.HashSet
import net.fwbrasil.activate.entity.id._
import net.fwbrasil.activate.entity.TestValidationEntity
import net.fwbrasil.activate.slick.SlickQueryContext
import net.fwbrasil.activate.storage.relational.async.AsyncMySQLStorage
import com.github.mauricio.async.db.mysql.pool.MySQLConnectionFactory

case class DummySeriablizable(val string: String)

object ActivateTestMigration {
    val timestamp = System.currentTimeMillis
}

abstract class ActivateTestMigration(
    implicit val ctx: ActivateTestContext)
    extends Migration {

    val timestamp = ActivateTestMigration.timestamp

    def up = {

        // Cascade option is ignored in MySql and Derby
        if (ctx == mysqlContext || ctx == asyncMysqlContext || ctx == derbyContext || ctx == sqlServerContext || ctx == polyglotContext)
            removeReferencesForAllEntities
                .ifExists

        removeAllEntitiesTables
            .ifExists
            .cascade

        createTableForAllEntities
            .ifNotExists

        createReferencesForAllEntities
            .ifNotExists

        createInexistentColumnsForAllEntities

        table[ctx.EntityByIntValue]
            .addIndex(
                columnName = "key",
                indexName = "IDX_KEY",
                unique = true)
            .ifNotExists
    }
}

abstract class ActivateTestMigrationCustomColumnType(recreate: Boolean = false)(
    implicit val ctx: ActivateTestContext)
    extends Migration {

    val timestamp = ActivateTestMigration.timestamp + 1

    def up =
        if (recreate) {
            table[ctx.ActivateTestEntity].removeColumn("bigStringValue")
            table[ctx.ActivateTestEntity].addColumn(_.customColumn[String]("bigStringValue", bigStringType))
        } else
            table[ctx.ActivateTestEntity].modifyColumnType(_.customColumn[String]("bigStringValue", bigStringType))

    def bigStringType: String

}

object prevaylerContext extends ActivateTestContext {
    lazy val storage = new PrevaylerStorage("testPrevalenceBase/testPrevaylerMemoryStorage" + (new java.util.Date).getTime)
}
class PrevaylerActivateTestMigration extends ActivateTestMigration()(prevaylerContext)

object prevalentContext extends ActivateTestContext {
    lazy val storage = new PrevalentStorage("testPrevalenceBase/testPrevalentStorage" + (new java.util.Date).getTime)
}
class PrevalentActivateTestMigration extends ActivateTestMigration()(prevaylerContext)

object memoryContext extends ActivateTestContext {
    val storage = new TransientMemoryStorage
}
class MemoryActivateTestMigration extends ActivateTestMigration()(memoryContext)

object mysqlContext extends ActivateTestContext with SlickQueryContext {
    System.getProperties.put("activate.storage.mysql.factory", "net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory")
    System.getProperties.put("activate.storage.mysql.jdbcDriver", "com.mysql.jdbc.Driver")
    System.getProperties.put("activate.storage.mysql.user", "root")
    System.getProperties.put("activate.storage.mysql.password", "root")
    System.getProperties.put("activate.storage.mysql.url", "jdbc:mysql://127.0.0.1/activate_test")
    System.getProperties.put("activate.storage.mysql.dialect", "mySqlDialect")
    lazy val storage =
        StorageFactory.fromSystemProperties("mysql").asInstanceOf[PooledJdbcRelationalStorage]
}
class MysqlActivateTestMigration extends ActivateTestMigration()(mysqlContext)
class MysqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(mysqlContext) {
    override def bigStringType = "TEXT"
}

object postgresqlContext extends ActivateTestContext {
    lazy val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.postgresql.Driver"
        val user = Some("postgres")
        val password = Some("postgres")
        val url = "jdbc:postgresql://127.0.0.1/activate_test"
        def normalize(string: String) =
            string.toLowerCase match {
                case "order" => "orderr"
                case other => other
            }
        val dialect = postgresqlDialect(escape = noEscape, normalize)
    }
}
class PostgresqlActivateTestMigration extends ActivateTestMigration()(postgresqlContext)
class PostgresqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(postgresqlContext) {
    override def bigStringType = "TEXT"
}

object asyncPostgresqlContext extends ActivateTestContext {
    lazy val storage = new AsyncPostgreSQLStorage {
        def configuration =
            new Configuration(
                username = "postgres",
                host = "localhost",
                password = Some("postgres"),
                database = Some("activate_test_async"))
        lazy val objectFactory = new PostgreSQLConnectionFactory(configuration)
        override def poolConfiguration = PoolConfiguration.Default.copy(maxQueueSize = 400, maxObjects = 5)
        override val dialect = postgresqlDialect(normalize = underscoreSeparated)
    }
}
class AsyncPostgresqlActivateTestMigration extends ActivateTestMigration()(asyncPostgresqlContext)
class AsyncPostgresqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(asyncPostgresqlContext) {
    override def bigStringType = "TEXT"
}

object asyncMysqlContext extends ActivateTestContext {
    lazy val storage = new AsyncMySQLStorage {
        def configuration =
            new Configuration(
                username = "root",
                host = "localhost",
                port = 3306,
                password = Some("root"),
                database = Some("activate_test_async"))
        lazy val objectFactory = new MySQLConnectionFactory(configuration)
        override def poolConfiguration = PoolConfiguration.Default.copy(maxQueueSize = 400, maxObjects = 5)
    }
}
class AsyncMysqlActivateTestMigration extends ActivateTestMigration()(asyncMysqlContext)
class AsyncMysqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(asyncMysqlContext) {
    override def bigStringType = "TEXT"
}

object h2Context extends ActivateTestContext {
    lazy val (storage, permanentConnectionToHoldMemoryDatabase) = {
        val storage = new SimpleJdbcRelationalStorage {
            val jdbcDriver = "org.h2.Driver"
            val user = "sa"
            val password = ""
            val url = "jdbc:h2:mem:activate_test_h2;DB_CLOSE_DELAY=-1"
            val dialect = h2Dialect
        }
        (storage, storage.getConnection)
    }
}

class H2ActivateTestMigration extends ActivateTestMigration()(h2Context)
class H2ActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(h2Context) {
    override def bigStringType = "CLOB"
}

object hsqldbContext extends ActivateTestContext {
    lazy val (storage, permanentConnectionToHoldMemoryDatabase) = {
        val storage = new SimpleJdbcRelationalStorage {
            val jdbcDriver = "org.hsqldb.jdbcDriver"
            val user = "sa"
            val password = ""
            val url = "jdbc:hsqldb:mem:activate_test_hsqldb"
            val dialect = hsqldbDialect
        }
        (storage, storage.getConnection)
    }
}

class HsqldbActivateTestMigration extends ActivateTestMigration()(hsqldbContext)
class HsqldbActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(hsqldbContext) {
    override def bigStringType = "CLOB"
}

object derbyContext extends ActivateTestContext {
    System.setProperty("derby.locks.deadlockTrace", "true")
    lazy val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.apache.derby.jdbc.EmbeddedDriver"
        val user = None
        val password = None
        val url = "jdbc:derby:memory:activate_test_derby;create=true"
        val dialect = derbyDialect
    }
}

class DerbyActivateTestMigration extends ActivateTestMigration()(derbyContext)
class DerbyActivateTestMigrationCustomColumnType extends Migration()(derbyContext) {

    val timestamp = ActivateTestMigration.timestamp + 1

    def up = {
        table[derbyContext.ActivateTestEntity].removeColumn("bigStringValue").ifExists
        table[derbyContext.ActivateTestEntity].addColumn(_.customColumn[String]("bigStringValue", "CLOB"))
    }
}

object mongoContext extends ActivateTestContext {
    lazy val storage = new MongoStorage {
        override val authentication = Option(("activate_test", "activate_test"))
        override val host = "localhost,localhost:27017"
        override val port = 27017
        override val db = "activate_test"
    }
}
class MongoActivateTestMigration extends ActivateTestMigration()(mongoContext)

object asyncMongoContext extends ActivateTestContext {
    lazy val storage = new AsyncMongoStorage {
        override val host = "localhost"
        override val port = 27017
        override val db = "activate_test_async"
    }
}
class AsyncMongoActivateTestMigration extends ActivateTestMigration()(asyncMongoContext)

object oracleContext extends ActivateTestContext {
    lazy val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
        val user = Some("activate_test")
        val password = Some("activate_test")
        val url = "jdbc:oracle:thin:@192.168.0.114:1521:orcl"
        val dialect = oracleDialect
        // Some oracle versions does not return the number of updated rows
        // when using batch operations correctly. Disable it.
        override val batchLimit = 0
    }
}
class OracleActivateTestMigration extends ActivateTestMigration()(oracleContext)
class OracleActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType(recreate = true)(oracleContext) {
    override def bigStringType = "CLOB"
}

//object asyncCassandraContext extends ActivateTestContext {
//    lazy val storage = new AsyncCassandraStorage {
//        def contactPoints = List("localhost")
//        def keyspace = "ACTIVATE_TEST"
//    }
//}
//class AsyncCassandraActivateTestMigration extends ActivateTestMigration()(asyncCassandraContext)
//class AdditionalAsyncCassandraActivateTestMigration extends Migration()(asyncCassandraContext) {
//    val timestamp = ActivateTestMigration.timestamp + 1
//    def up = {
//        for (clazz <- EntityHelper.allConcreteEntityClasses) {
//            val t = table(manifestClass(clazz))
//            val metadata = EntityHelper.getEntityMetadata(clazz)
//            for (
//                property <- metadata.persistentPropertiesMetadata;
//                if (property.name != "id" &&
//                    !property.isTransient &&
//                    property.propertyType != classOf[List[_]] &&
//                    property.propertyType != classOf[LazyList[_]])
//            ) t.addIndex(property.name, metadata.name + "Ix" + property.name.capitalize).ifNotExists
//        }
//    }
//}

object db2Context extends ActivateTestContext {
    lazy val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "com.ibm.db2.jcc.DB2Driver"
        val user = Some("db2admin")
        val password = Some("db2admin")
        val url = "jdbc:db2://192.168.0.114:50000/SAMPLE"
        val dialect = db2Dialect
    }
}
class Db2ActivateTestMigration extends ActivateTestMigration()(db2Context)
class Db2ActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType(recreate = true)(db2Context) {
    override def bigStringType = "CLOB"
}

object sqlServerContext extends ActivateTestContext {
    lazy val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "net.sourceforge.jtds.jdbc.Driver"
        val user = Some("activate")
        val password = Some("activate")
        val url = "jdbc:jtds:sqlserver://192.168.0.114:49503/activate_test2"
        val dialect = sqlServerDialect
    }
}
class SqlServerActivateTestMigration extends ActivateTestMigration()(sqlServerContext)
class SqlServerActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(sqlServerContext) {
    override def bigStringType = "TEXT"
}

object polyglotContext extends ActivateTestContext {
    lazy val postgre = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.postgresql.Driver"
        val user = Some("postgres")
        val password = Some("postgres")
        val url = "jdbc:postgresql://127.0.0.1/activate_test_polyglot"
        val dialect = postgresqlDialect
    }
    lazy val asyncPostgre = new AsyncPostgreSQLStorage {
        def configuration =
            new Configuration(
                username = "postgres",
                host = "localhost",
                password = Some("postgres"),
                database = Some("activate_test_polyglot_async"))
        lazy val objectFactory = new PostgreSQLConnectionFactory(configuration)
        override def poolConfiguration = PoolConfiguration.Default.copy(maxQueueSize = 400, maxObjects = 5)
        override val dialect = postgresqlDialect
    }
    lazy val mysql = new PooledJdbcRelationalStorage {
        val jdbcDriver = "com.mysql.jdbc.Driver"
        val user = Some("root")
        val password = Some("root")
        val url = "jdbc:mysql://127.0.0.1/activate_test_polyglot"
        val dialect = mySqlDialect
    }
    lazy val prevayler = new PrevaylerStorage("testPrevalenceBase/testPolyglotPrevaylerMemoryStorage" + (new java.util.Date).getTime)
    lazy val mongo = new MongoStorage {
        override val authentication = Option(("activate_test", "activate_test"))
        override val host = "localhost"
        override val port = 27017
        override val db = "activate_test_polyglot"
    }
    lazy val asyncMongo = new AsyncMongoStorage {
        override val host = "localhost"
        override val port = 27017
        override val db = "activate_test_polyglot_async"
    }

    lazy val derby = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.apache.derby.jdbc.EmbeddedDriver"
        val user = None
        val password = None
        val url = "jdbc:derby:memory:activate_test_derby_polygot;create=true"
        val dialect = derbyDialect
    }
    lazy val h2 = new SimpleJdbcRelationalStorage {
        val jdbcDriver = "org.h2.Driver"
        val user = "sa"
        val password = ""
        val url = "jdbc:h2:mem:activate_test_h2_polyglot;DB_CLOSE_DELAY=-1"
        val dialect = h2Dialect
    }
    lazy val memory = new TransientMemoryStorage
    lazy val storage = mysql
    override def additionalStorages = Map(
        derby -> Set(classOf[Num]),
        h2 -> Set(classOf[EntityWithUninitializedValue]),
        memory -> Set(classOf[SimpleEntity]),
        asyncMongo -> Set(classOf[EntityWithoutAttribute]),
        mongo -> Set(classOf[Box]),
        postgre -> Set(classOf[ActivateTestEntity], classOf[TraitAttribute], classOf[TraitAttribute1], classOf[TraitAttribute2]),
        asyncPostgre -> Set(classOf[Employee]),
        prevayler -> Set(classOf[CaseClassEntity]))
}
class PolyglotActivateTestMigration extends ActivateTestMigration()(polyglotContext)
class PolyglotActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(polyglotContext) {
    override def bigStringType = "TEXT"
}

object BigStringGenerator {
    val generated = {
        var s = ""
        for (i <- 0 until 400)
            s += "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
        s
    }

}

trait ActivateTestContext
    extends StoppableActivateContext {

    override val milisToWaitBeforeRetry = 1

    override def liveCacheType =
        Random.nextInt(3) match {
            case 0 =>
                CacheType.hardReferences
            case 1 =>
                CacheType.softReferences
            case 2 =>
                CacheType.weakReferences
        }

    override def customCaches =
        List(
            CustomCache[ActivateTestEntity](
                cacheType = CacheType.hardReferences),
            CustomCache[Box](
                cacheType = CacheType.softReferences,
                condition = _.id.charAt(0).toInt < 40),
            CustomCache[CaseClassEntity](
                cacheType = CacheType.weakReferences,
                transactionalCondition = true,
                condition = _.stringValue == fullStringValue))

    override protected def entitiesPackages = List("customPackage")

    class TestValidationEntityIdGenerator
        extends SegmentedIdGenerator[TestValidationEntity](
            IntSequenceEntity(
                "testValidationEntityId",
                step = 5))

    val indexActivateTestEntityByIntValue = memoryIndex[ActivateTestEntity].on(_.intValue)

    class EntityByIntValue(val key: Int) extends PersistedIndexEntry[Int, ActivateTestEntity] with UUID {
        var ids =
            new HashSet[String] ++ query {
                (e: ActivateTestEntity) => where(e.intValue :== key) select (e.id)
            }
    }

    val persistedIndexActivateTestEntityByIntValue =
        persistedIndex[ActivateTestEntity].on(_.intValue).using[EntityByIntValue] {
            key =>
                query {
                    (entry: EntityByIntValue) => where(entry.key :== key) select (entry)
                }.headOption.getOrElse {
                    new EntityByIntValue(key)
                }
        }

    override def executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

    override protected[activate] def entityMaterialized(entity: net.fwbrasil.activate.entity.BaseEntity) =
        if (entity.getClass.getDeclaringClass == classOf[ActivateTestContext])
            Reflection.set(entity, "$outer", this)

    override protected val defaultSerializer = xmlSerializer
    override protected def customSerializers = List(
        serialize[ActivateTestEntity](_.tupleOptionValue) using jsonSerializer)

    val emptyIntValue = 0
    val emptyLongValue = 0l
    val emptyBooleanValue = false
    val emptyCharValue = 'N'
    val emptyStringValue = null
    val emptyLazyValue = null
    val emptyFloatValue = 0f
    val emptyDoubleValue = 0d
    val emptyBigDecimalValue = null
    val emptyDateValue = null
    val emptyCalendarValue = null
    val emptyTraitValue1 = null
    val emptyTraitValue2 = null
    val emptyByteArrayValue = null
    val emptyEntityValue = null
    val emptyEnumerationValue = null
    val emptyJodaInstantValue = null
    val emptyOptionValue = None
    val emptyOptionWithPrimitiveValue = None
    val emptyEntityWithoutAttributeValue = null
    val emptyCaseClassEntityValue = null
    val emptySerializableEntityValue = null
    val emptyListEntityValue = LazyList[ActivateTestEntity]()
    val emptyTupleOptionValue = None

    val fullIntValue = 999
    val fullLongValue = 999l
    val fullBooleanValue = true
    val fullCharValue = 'A'
    val fullStringValue = "S"
    val fullLazyValue = "L"
    val fullFloatValue = 0.1f
    val fullDoubleValue = 1d
    val fullBigDecimalValue = BigDecimal(1)
    val fullDateValue = new java.util.Date(98977898)
    val fullEnumerationValue = EnumerationValue.value1a
    val fullCalendarValue = {
        val cal = java.util.Calendar.getInstance()
        cal.setTimeInMillis(98977898)
        cal
    }

    def fullTraitValue1 =
        all[TraitAttribute1].headOption.getOrElse(
            new TraitAttribute1("1"))

    def fullTraitValue2 =
        all[TraitAttribute2].headOption.getOrElse(
            new TraitAttribute2("2"))

    val fullByteArrayValue = "S".getBytes
    def fullEntityValue =
        cachedQuery {
            (e: ActivateTestEntity) => where(e.dummy :== true)
        }.headOption.getOrElse {
            val entity = newEmptyActivateTestEntity
            entity.dummy = true
            entity
        }

    val fullJodaInstantValue = new DateTime(78317811l)
    val fullOptionValue = Some("string")
    val fullOptionWithPrimitiveValue = Some(1)
    def fullEntityWithoutAttributeValue =
        cachedQuery {
            (e: EntityWithoutAttribute) => where()
        }.headOption.getOrElse {
            new EntityWithoutAttribute
        }

    def fullCaseClassEntityValue =
        all[CaseClassEntity].headOption.getOrElse(
            new CaseClassEntity(fullStringValue, fullEntityValue, fullEntityWithoutAttributeValue))

    val fullSerializableEntityValue =
        new DummySeriablizable("dummy")

    def fullListEntityValue = List(fullEntityValue)

    val fullTupleOptionValue = Some(1, 1)

    def setFullEntity(entity: ActivateTestEntity) = {
        entity.intValue = fullIntValue
        entity.longValue = fullLongValue
        entity.booleanValue = fullBooleanValue
        entity.charValue = fullCharValue
        entity.stringValue = fullStringValue
        entity.floatValue = fullFloatValue
        entity.doubleValue = fullDoubleValue
        entity.bigDecimalValue = fullBigDecimalValue
        entity.dateValue = fullDateValue
        entity.jodaInstantValue = fullJodaInstantValue
        entity.calendarValue = fullCalendarValue
        entity.byteArrayValue = fullByteArrayValue
        entity.entityValue = fullEntityValue
        entity.traitValue1 = fullTraitValue1
        entity.traitValue2 = fullTraitValue2
        entity.enumerationValue = fullEnumerationValue
        entity.optionValue = fullOptionValue
        entity.optionWithPrimitiveValue = fullOptionWithPrimitiveValue
        entity.entityWithoutAttributeValue = fullEntityWithoutAttributeValue
        entity.caseClassEntityValue = fullCaseClassEntityValue
        entity.serializableEntityValue = fullSerializableEntityValue
        entity.listEntityValue = fullListEntityValue
        entity.unitializedList = List(1, 2)
        entity.tupleOptionValue = fullTupleOptionValue
        entity
    }

    def setEmptyEntity(entity: ActivateTestEntity) = {
        entity.intValue = emptyIntValue
        entity.longValue = emptyLongValue
        entity.booleanValue = emptyBooleanValue
        entity.charValue = emptyCharValue
        entity.stringValue = emptyStringValue
        entity.floatValue = emptyFloatValue
        entity.doubleValue = emptyDoubleValue
        entity.bigDecimalValue = emptyBigDecimalValue
        entity.dateValue = emptyDateValue
        entity.jodaInstantValue = emptyJodaInstantValue
        entity.calendarValue = emptyCalendarValue
        entity.byteArrayValue = emptyByteArrayValue
        entity.entityValue = emptyEntityValue
        entity.traitValue1 = emptyTraitValue1
        entity.traitValue2 = emptyTraitValue2
        entity.enumerationValue = emptyEnumerationValue
        entity.optionValue = emptyOptionValue
        entity.optionWithPrimitiveValue = emptyOptionWithPrimitiveValue
        entity.entityWithoutAttributeValue = emptyEntityWithoutAttributeValue
        entity.caseClassEntityValue = emptyCaseClassEntityValue
        entity.serializableEntityValue = emptySerializableEntityValue
        entity.listEntityValue = emptyListEntityValue
        entity.unitializedList = List()
        entity.tupleOptionValue = emptyTupleOptionValue
        entity
    }

    def newEmptyActivateTestEntity =
        setEmptyEntity(newTestEntity())
    def newFullActivateTestEntity =
        setFullEntity(newTestEntity())

    class Supplier(var name: String, var city: String) extends Entity
    class Coffee(var name: String, var supplier: Supplier, var price: Double) extends Entity

    class SimpleEntity(var intValue: Int) extends Entity

    trait TraitAttribute extends Entity {
        var attribute: String
    }

    class TraitAttribute1 private (var attribute: String, val dummy: String) extends TraitAttribute {
        def this(attribute: String) =
            this(attribute, attribute)
        def testTraitAttribute = attribute
    }

    class TraitAttribute2(var attribute: String) extends TraitAttribute {
        def testTraitAttribute = attribute
    }

    class Employee(var name: String, var supervisor: Option[Employee]) extends Entity {
        def subordinates = select[Employee] where (_.supervisor :== Some(this))
    }

    class EntityWithoutAttribute extends Entity

    class EntityWithUninitializedValue extends Entity {
        var uninitializedValue: String = _
    }

    abstract class Page(var title: String, parent: Option[Page])
        extends Entity {
        def toXHtml: Elem
    }

    class BasicPage(pTitle: String, var content: String, parent: Option[Page] = None)
        extends Page(pTitle, parent) {
        def toXHtml = <div class="row">
                          <div class="col-md-12">
                              <span class="label label-info">{ title }</span>
                              <hr/>
                              <p>{ content }</p>
                          </div>
                      </div>

        protected def invariantTitleMustBeLongEnough =
            on(_.title).invariant(title.size > 3)

        protected def invariantContentMustBeLongEnough =
            on(_.content).invariant(content.size > 3)
    }

    class X[T <: Entity] extends Entity {
        var p: Option[T] = None
    }

    case class CaseClassEntity(
        var stringValue: String,
        var entityValue: ActivateTestEntity,
        var entityWithoutAttributeValue: EntityWithoutAttribute) extends Entity {
        var customEncodedEntityValue = new CustomEncodedEntityValue(1)
        var userStatus: UserStatus = NormalUser
    }

    abstract class ActivateTestDummyEntity(var dummy: Boolean) extends Entity

    // Relational reserved words
    class Order(var key: String) extends Entity

    // Short name entity
    @Alias("sne")
    class ShortNameEntity(var string: String) extends Entity

    trait TreeNode[N <: TreeNode[N]] extends Entity {

        implicit protected def m: Manifest[N]

        var parent: Option[N] = None

        def children: List[N] = transactional {
            query {
                (node: N) => where(node.parent :== this) select (node)
            }
        }

        def toPath: String =
            List(Some(toString), parent.map(_.toPath)).flatten.mkString(" / ")
    }

    class Box(var contains: List[Num] = Nil) extends TreeNode[Box] {
        protected def m = manifest[Box]
        def add(n: Int) = {
            val num = new Num(n)
            contains = num :: contains
            num
        }
    }

    class Num(
        var num: Int) extends Entity

    object ActivateTestEntity {
        def all = ActivateTestContext.this.all[ActivateTestEntity]
        var onModifyFloatCallback = (oldValue: Float, newValue: Float) => {}
        var lifecycleCallback = (event: String) => {}
    }

    class ActivateTestEntity(
        var intValue: Int,
        var longValue: Long,
        var booleanValue: Boolean,
        var charValue: Char,
        var stringValue: String,
        var floatValue: Float,
        var doubleValue: Double,
        var bigDecimalValue: BigDecimal,
        var dateValue: java.util.Date,
        var jodaInstantValue: DateTime,
        var calendarValue: java.util.Calendar,
        var byteArrayValue: Array[Byte],
        var entityValue: ActivateTestEntity,
        var traitValue1: TraitAttribute,
        var traitValue2: TraitAttribute,
        var enumerationValue: EnumerationValue,
        lazyValueValue: String,
        var optionValue: Option[String],
        var optionWithPrimitiveValue: Option[Int],
        var entityWithoutAttributeValue: EntityWithoutAttribute,
        var caseClassEntityValue: CaseClassEntity,
        var serializableEntityValue: DummySeriablizable,
        @Alias("ATEListEntityValue") var listEntityValue: LazyList[ActivateTestEntity],
        var tupleOptionValue: Option[(Int, Int)],
        @Alias("customNameConst") var customNamedConstructorProperty: String) extends ActivateTestDummyEntity(false) {

        def this(intValue: Int) = this(
            intValue * 2,
            emptyLongValue,
            emptyBooleanValue,
            emptyCharValue,
            emptyStringValue,
            emptyFloatValue,
            emptyDoubleValue,
            emptyBigDecimalValue,
            emptyDateValue,
            emptyJodaInstantValue,
            emptyCalendarValue,
            emptyByteArrayValue,
            emptyEntityValue,
            emptyTraitValue1,
            emptyTraitValue2,
            emptyEnumerationValue,
            emptyLazyValue,
            emptyOptionValue,
            emptyOptionWithPrimitiveValue,
            emptyEntityWithoutAttributeValue,
            emptyCaseClassEntityValue,
            emptySerializableEntityValue,
            emptyListEntityValue,
            emptyTupleOptionValue,
            emptyStringValue)

        ActivateTestEntity.lifecycleCallback("insideConstructor")

        override protected def beforeConstruct = ActivateTestEntity.lifecycleCallback("beforeConstruct")
        override protected def afterConstruct = ActivateTestEntity.lifecycleCallback("afterConstruct")
        override protected def beforeInitialize = ActivateTestEntity.lifecycleCallback("beforeInitialize")
        override protected def afterInitialize = ActivateTestEntity.lifecycleCallback("afterInitialize")
        override protected def beforeDelete = ActivateTestEntity.lifecycleCallback("beforeDelete")
        override protected def afterDelete = ActivateTestEntity.lifecycleCallback("afterDelete")
        override def beforeInsert = ActivateTestEntity.lifecycleCallback("beforeInsert")
        override def beforeUpdate = ActivateTestEntity.lifecycleCallback("beforeUpdate")
        override def beforeInsertOrUpdate = ActivateTestEntity.lifecycleCallback("beforeInsertOrUpdate")

        def onModifyFloat =
            on(_.floatValue).change {
                ActivateTestEntity.onModifyFloatCallback(originalValue(_.floatValue), floatValue)
            }

        lazy val lazyValue = lazyValueValue
        var varInitializedInConstructor = fullStringValue
        val valInitializedInConstructor = fullStringValue
        val calculatedInConstructor = intValue * 2
        var bigStringValue = BigStringGenerator.generated
        var unitializedList: List[Int] = _
        @Alias("customName")
        var customNamedValue = fullStringValue
        var length = 0
        @transient val transientValue = new Object
        @transient lazy val transientLazyValue = new Object

        override def toString = s"ActivateTestEntity(id=$id)"
    }

    def validateFullTestEntity(entity: ActivateTestEntity = null,
                               intValue: Int = fullIntValue,
                               longValue: Long = fullLongValue,
                               booleanValue: Boolean = fullBooleanValue,
                               charValue: Char = fullCharValue,
                               stringValue: String = fullStringValue,
                               floatValue: Float = fullFloatValue,
                               doubleValue: Double = fullDoubleValue,
                               bigDecimalValue: BigDecimal = fullBigDecimalValue,
                               dateValue: java.util.Date = fullDateValue,
                               jodaInstantValue: DateTime = fullJodaInstantValue,
                               calendarValue: java.util.Calendar = fullCalendarValue,
                               byteArrayValue: Array[Byte] = fullByteArrayValue,
                               entityValue: ActivateTestEntity = fullEntityValue,
                               traitValue1: TraitAttribute = fullTraitValue1,
                               traitValue2: TraitAttribute = fullTraitValue2,
                               enumerationValue: EnumerationValue = fullEnumerationValue,
                               lazyValue: String = fullLazyValue,
                               optionValue: Option[String] = fullOptionValue,
                               entityWithoutAttributeValue: EntityWithoutAttribute = fullEntityWithoutAttributeValue,
                               caseClassEntityValue: CaseClassEntity = fullCaseClassEntityValue,
                               serializableEntityValue: DummySeriablizable = fullSerializableEntityValue,
                               listEntityValue: LazyList[ActivateTestEntity] = fullListEntityValue,
                               tupleOptionValue: Option[(Int, Int)] = fullTupleOptionValue) =

        validateEmptyTestEntity(
            entity,
            intValue,
            longValue,
            booleanValue,
            charValue,
            stringValue,
            floatValue,
            doubleValue,
            bigDecimalValue,
            dateValue,
            jodaInstantValue,
            calendarValue,
            byteArrayValue,
            entityValue,
            traitValue1,
            traitValue2,
            enumerationValue,
            lazyValue,
            optionValue,
            entityWithoutAttributeValue,
            caseClassEntityValue,
            serializableEntityValue,
            listEntityValue,
            tupleOptionValue)

    def validateEmptyTestEntity(entity: ActivateTestEntity = null,
                                intValue: Int = emptyIntValue,
                                longValue: Long = emptyLongValue,
                                booleanValue: Boolean = emptyBooleanValue,
                                charValue: Char = emptyCharValue,
                                stringValue: String = emptyStringValue,
                                floatValue: Float = emptyFloatValue,
                                doubleValue: Double = emptyDoubleValue,
                                bigDecimalValue: BigDecimal = emptyBigDecimalValue,
                                dateValue: java.util.Date = emptyDateValue,
                                jodaInstantValue: DateTime = emptyJodaInstantValue,
                                calendarValue: java.util.Calendar = emptyCalendarValue,
                                byteArrayValue: Array[Byte] = emptyByteArrayValue,
                                entityValue: ActivateTestEntity = emptyEntityValue,
                                traitValue1: TraitAttribute = emptyTraitValue1,
                                traitValue2: TraitAttribute = emptyTraitValue2,
                                enumerationValue: EnumerationValue = emptyEnumerationValue,
                                lazyValue: String = emptyLazyValue,
                                optionValue: Option[String] = emptyOptionValue,
                                entityWithoutAttributeValue: EntityWithoutAttribute = emptyEntityWithoutAttributeValue,
                                caseClassEntityValue: CaseClassEntity = emptyCaseClassEntityValue,
                                serializableEntityValue: DummySeriablizable = emptySerializableEntityValue,
                                listEntityValue: LazyList[ActivateTestEntity] = emptyListEntityValue,
                                tupleOptionValue: Option[(Int, Int)] = emptyTupleOptionValue) = {

        require(entity.intValue == intValue)
        require(entity.longValue == longValue)
        require(entity.booleanValue == booleanValue)
        require(entity.charValue == charValue)
        require(entity.stringValue == stringValue)
        require(entity.floatValue == floatValue)
        require(entity.doubleValue == doubleValue)
        require(entity.bigDecimalValue == bigDecimalValue)
        require(entity.dateValue == dateValue)
        require(entity.jodaInstantValue == jodaInstantValue)
        require(entity.calendarValue == calendarValue)
        require(entity.entityValue == entityValue)
        require(entity.traitValue1 == traitValue1)
        require(entity.traitValue2 == traitValue2)
        require(entity.enumerationValue == enumerationValue)
        require(entity.optionValue == optionValue)
        require(entity.entityWithoutAttributeValue == entityWithoutAttributeValue)
        require(entity.serializableEntityValue == serializableEntityValue)
        require(entity.listEntityValue == listEntityValue)
        require(entity.unitializedList == List() || entity.unitializedList == List(1, 2))
        require(entity.tupleOptionValue == tupleOptionValue)

    }

    def newTestEntity(
        intValue: Int = emptyIntValue,
        longValue: Long = emptyLongValue,
        booleanValue: Boolean = emptyBooleanValue,
        charValue: Char = emptyCharValue,
        stringValue: String = emptyStringValue,
        floatValue: Float = emptyFloatValue,
        doubleValue: Double = emptyDoubleValue,
        bigDecimalValue: BigDecimal = emptyBigDecimalValue,
        dateValue: java.util.Date = emptyDateValue,
        jodaInstantValue: DateTime = emptyJodaInstantValue,
        calendarValue: java.util.Calendar = emptyCalendarValue,
        byteArrayValue: Array[Byte] = emptyByteArrayValue,
        entityValue: ActivateTestEntity = emptyEntityValue,
        traitValue1: TraitAttribute = emptyTraitValue1,
        traitValue2: TraitAttribute = emptyTraitValue2,
        enumerationValue: EnumerationValue = emptyEnumerationValue,
        lazyValue: String = emptyLazyValue,
        optionValue: Option[String] = emptyOptionValue,
        optionWithPrimitiveValue: Option[Int] = emptyOptionWithPrimitiveValue,
        entityWithoutAttributeValue: EntityWithoutAttribute = emptyEntityWithoutAttributeValue,
        caseClassEntityValue: CaseClassEntity = emptyCaseClassEntityValue,
        serializableEntityValue: DummySeriablizable = emptySerializableEntityValue,
        listEntityValue: LazyList[ActivateTestEntity] = emptyListEntityValue,
        tupleOptionValue: Option[(Int, Int)] = emptyTupleOptionValue) = {
        new ActivateTestEntity(
            intValue = intValue,
            longValue = longValue,
            booleanValue = booleanValue,
            charValue = charValue,
            stringValue = stringValue,
            floatValue = floatValue,
            doubleValue = doubleValue,
            bigDecimalValue = bigDecimalValue,
            dateValue = dateValue,
            jodaInstantValue = jodaInstantValue,
            calendarValue = calendarValue,
            byteArrayValue = byteArrayValue,
            entityValue = entityValue,
            traitValue1 = traitValue1,
            traitValue2 = traitValue2,
            enumerationValue = enumerationValue,
            lazyValueValue = lazyValue,
            optionValue = optionValue,
            optionWithPrimitiveValue = optionWithPrimitiveValue,
            entityWithoutAttributeValue = entityWithoutAttributeValue,
            caseClassEntityValue = caseClassEntityValue,
            serializableEntityValue = serializableEntityValue,
            listEntityValue = listEntityValue,
            tupleOptionValue = tupleOptionValue,
            customNamedConstructorProperty = fullStringValue)
    }
}