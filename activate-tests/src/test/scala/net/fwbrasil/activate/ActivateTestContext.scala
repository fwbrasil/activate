package net.fwbrasil.activate

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
import net.fwbrasil.activate.storage.relational.async.AsyncJdbcRelationalStorage
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.postgresql.pool.PostgreSQLConnectionFactory
import org.jboss.netty.util.CharsetUtil
import com.github.mauricio.async.db.pool.ObjectFactory
import com.github.mauricio.async.db.Connection
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import com.github.mauricio.async.db.mysql.pool.MySQLConnectionFactory
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.pool.PoolConfiguration
import scala.concurrent.ExecutionContext
import scala.slick.direct.AnnotationMapper.table
import java.util.concurrent.Executors
import net.fwbrasil.activate.slick.SlickQueryContext
import net.fwbrasil.activate.storage.mongo.async.AsyncMongoStorage

object EnumerationValue extends Enumeration {
    case class EnumerationValue(name: String) extends Val(name)
    val value1a = EnumerationValue("v1")
    val value2 = EnumerationValue("v2")
    val value3 = EnumerationValue("v3")
}
import EnumerationValue._

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
        if (ctx == mysqlContext || ctx == asyncMysqlContext || ctx == derbyContext)
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
    }
}

abstract class ActivateTestMigrationCustomColumnType(
    implicit val ctx: ActivateTestContext)
        extends Migration {

    val timestamp = ActivateTestMigration.timestamp + 1

    def up = {
        table[ctx.ActivateTestEntity].removeColumn("bigStringValue").ifExists
        table[ctx.ActivateTestEntity].addColumn(_.customColumn[String]("bigStringValue", bigStringType))
    }

    def bigStringType: String

}

object prevaylerContext extends ActivateTestContext {
    val storage = new PrevaylerStorage("testPrevalenceBase/testPrevaylerMemoryStorage" + (new java.util.Date).getTime)
}
class PrevaylerActivateTestMigration extends ActivateTestMigration()(prevaylerContext)

object memoryContext extends ActivateTestContext {
    val storage = new TransientMemoryStorage
}
class MemoryActivateTestMigration extends ActivateTestMigration()(memoryContext)

object mysqlContext extends ActivateTestContext {
    System.getProperties.put("activate.storage.mysql.factory", "net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory")
    System.getProperties.put("activate.storage.mysql.jdbcDriver", "com.mysql.jdbc.Driver")
    System.getProperties.put("activate.storage.mysql.user", "root")
    System.getProperties.put("activate.storage.mysql.password", "root")
    System.getProperties.put("activate.storage.mysql.url", "jdbc:mysql://127.0.0.1/activate_test")
    System.getProperties.put("activate.storage.mysql.dialect", "mySqlDialect")
    val storage =
        StorageFactory.fromSystemProperties("mysql")
}
class MysqlActivateTestMigration extends ActivateTestMigration()(mysqlContext)
class MysqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(mysqlContext) {
    override def bigStringType = "TEXT"
}

object postgresqlContext extends ActivateTestContext with SlickQueryContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.postgresql.Driver"
        val user = "postgres"
        val password = "postgres"
        val url = "jdbc:postgresql://127.0.0.1/activate_test"
        val dialect = postgresqlDialect
    }
}
class PostgresqlActivateTestMigration extends ActivateTestMigration()(postgresqlContext)
class PostgresqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(postgresqlContext) {
    override def bigStringType = "TEXT"
}

object asyncPostgresqlContext extends ActivateTestContext {
    val storage = new AsyncJdbcRelationalStorage[PostgreSQLConnection] {
        def configuration =
            new Configuration(
                username = "postgres",
                host = "localhost",
                password = Some("postgres"),
                database = Some("activate_test_async"))
        lazy val objectFactory = new PostgreSQLConnectionFactory(configuration)
        override def poolConfiguration = PoolConfiguration.Default.copy(maxQueueSize = 400, maxObjects = 5)
        val dialect = postgresqlDialect
    }
}
class AsyncPostgresqlActivateTestMigration extends ActivateTestMigration()(asyncPostgresqlContext)
class AsyncPostgresqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(asyncPostgresqlContext) {
    override def bigStringType = "TEXT"
}

object asyncMysqlContext extends ActivateTestContext {
    val storage = new AsyncJdbcRelationalStorage[MySQLConnection] {
        def configuration =
            new Configuration(
                username = "root",
                host = "localhost",
                port = 3306,
                password = Some("root"),
                database = Some("activate_test_async"))
        lazy val objectFactory = new MySQLConnectionFactory(configuration)
        val dialect = mySqlDialect
    }
}
class AsyncMysqlActivateTestMigration extends ActivateTestMigration()(asyncMysqlContext)
class AsyncMysqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(asyncMysqlContext) {
    override def bigStringType = "TEXT"
}

object h2Context extends ActivateTestContext with SlickQueryContext {
    val storage = new SimpleJdbcRelationalStorage {
        val jdbcDriver = "org.h2.Driver"
        val user = "sa"
        val password = ""
        val url = "jdbc:h2:mem:activate_test_h2;DB_CLOSE_DELAY=-1"
        val dialect = h2Dialect
    }
    val permanentConnectionToHoldMemoryDatabase = storage.getConnection
}

class H2ActivateTestMigration extends ActivateTestMigration()(h2Context)
class H2ActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(h2Context) {
    override def bigStringType = "CLOB"
}

object hsqldbContext extends ActivateTestContext with SlickQueryContext {
    val storage = new SimpleJdbcRelationalStorage {
        val jdbcDriver = "org.hsqldb.jdbcDriver"
        val user = "sa"
        val password = ""
        val url = "jdbc:hsqldb:mem:activate_test_hsqldb"
        val dialect = hsqldbDialect
    }
    val permanentConnectionToHoldMemoryDatabase = storage.getConnection
}

class HsqldbActivateTestMigration extends ActivateTestMigration()(hsqldbContext)
class HsqldbActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(hsqldbContext) {
    override def bigStringType = "CLOB"
}

object derbyContext extends ActivateTestContext with SlickQueryContext {
    System.setProperty("derby.locks.deadlockTrace", "true")
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.apache.derby.jdbc.EmbeddedDriver"
        val user = ""
        val password = ""
        val url = "jdbc:derby:memory:activate_test_derby;create=true"
        val dialect = derbyDialect
    }
}

class DerbyActivateTestMigration extends ActivateTestMigration()(derbyContext)
class DerbyActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(derbyContext) {
    override def bigStringType = "CLOB"
}

object mongoContext extends ActivateTestContext {
    val storage = new MongoStorage {
        override val authentication = Option(("activate_test", "activate_test"))
        override val host = "localhost"
        override val port = 27017
        override val db = "activate_test"
    }
}
class MongoActivateTestMigration extends ActivateTestMigration()(mongoContext)

object asyncMongoContext extends ActivateTestContext {
    val storage = new AsyncMongoStorage {
        override val host = "localhost"
        override val port = 27017
        override val db = "activate_test_async"
    }
}
class AsyncMongoActivateTestMigration extends ActivateTestMigration()(asyncMongoContext)

object oracleContext extends ActivateTestContext with SlickQueryContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
        val user = "activate_test"
        val password = "activate_test"
        val url = "jdbc:oracle:thin:@192.168.1.8:1521:orcl"
        val dialect = oracleDialect
        // Some oracle versions does not return the number of updated rows
        // when using batch operations correctly. Disable it.
        override val batchLimit = 0
    }
}
class OracleActivateTestMigration extends ActivateTestMigration()(oracleContext)
class OracleActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(oracleContext) {
    override def bigStringType = "CLOB"
}

object db2Context extends ActivateTestContext with SlickQueryContext {
    val storage = new PooledJdbcRelationalStorage {
        val jdbcDriver = "com.ibm.db2.jcc.DB2Driver"
        val user = "db2inst1"
        val password = "db2inst1"
        val url = "jdbc:db2://192.168.1.8:50000/ACTTST"
        val dialect = db2Dialect
    }
}
class Db2ActivateTestMigration extends ActivateTestMigration()(db2Context)
class Db2ActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(db2Context) {
    override def bigStringType = "CLOB"
}

object polyglotContext extends ActivateTestContext {
    val postgre = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.postgresql.Driver"
        val user = "postgres"
        val password = "postgres"
        val url = "jdbc:postgresql://127.0.0.1/activate_test_polyglot"
        val dialect = postgresqlDialect
    }
    val asyncPostgre = new AsyncJdbcRelationalStorage[PostgreSQLConnection] {
        def configuration =
            new Configuration(
                username = "postgres",
                host = "localhost",
                password = Some("postgres"),
                database = Some("activate_test_polyglot_async"))
        lazy val objectFactory = new PostgreSQLConnectionFactory(configuration)
        override def poolConfiguration = PoolConfiguration.Default.copy(maxQueueSize = 400, maxObjects = 5)
        val dialect = postgresqlDialect
    }
    val mysql = new PooledJdbcRelationalStorage {
        val jdbcDriver = "com.mysql.jdbc.Driver"
        val user = "root"
        val password = "root"
        val url = "jdbc:mysql://127.0.0.1/activate_test_polyglot"
        val dialect = mySqlDialect
    }
    val prevayler = new PrevaylerStorage("testPrevalenceBase/testPolyglotPrevaylerMemoryStorage" + (new java.util.Date).getTime)
    val mongo = new MongoStorage {
        override val authentication = Option(("activate_test", "activate_test"))
        override val host = "localhost"
        override val port = 27017
        override val db = "activate_test_polyglot"
    }
    val asyncMongo = new AsyncMongoStorage {
        override val host = "localhost"
        override val port = 27017
        override val db = "activate_test_polyglot_async"
    }

    val derby = new PooledJdbcRelationalStorage {
        val jdbcDriver = "org.apache.derby.jdbc.EmbeddedDriver"
        val user = ""
        val password = ""
        val url = "jdbc:derby:memory:activate_test_derby_polygot;create=true"
        val dialect = derbyDialect
    }
    val h2 = new SimpleJdbcRelationalStorage {
        val jdbcDriver = "org.h2.Driver"
        val user = "sa"
        val password = ""
        val url = "jdbc:h2:mem:activate_test_h2_polyglot;DB_CLOSE_DELAY=-1"
        val dialect = h2Dialect
    }
    val memory = new TransientMemoryStorage
    val storage = postgre
    override def additionalStorages = Map(
        derby -> Set(classOf[Num]),
        h2 -> Set(classOf[EntityWithUninitializedValue]),
        memory -> Set(classOf[SimpleEntity], classOf[EntityWithoutAttribute]),
        asyncMongo -> Set(classOf[EntityWithoutAttribute]),
        mongo -> Set(classOf[Box]),
        mysql -> Set(classOf[ActivateTestEntity]),
        asyncPostgre -> Set(classOf[TraitAttribute], classOf[TraitAttribute1], classOf[TraitAttribute2]),
        prevayler -> Set(classOf[Employee], classOf[CaseClassEntity]))
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

    override def executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(20))

    override protected[activate] def entityMaterialized(entity: Entity) =
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
        select[ActivateTestEntity].where(_.dummy :== true).headOption.getOrElse({
            val entity = newEmptyActivateTestEntity
            entity.dummy = true
            entity
        })

    val fullJodaInstantValue = new DateTime(78317811l)
    val fullOptionValue = Some("string")
    val fullOptionWithPrimitiveValue = Some(1)
    def fullEntityWithoutAttributeValue =
        all[EntityWithoutAttribute].headOption.getOrElse(
            new EntityWithoutAttribute)

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

    case class CaseClassEntity(
        var stringValue: String,
        var entityValue: ActivateTestEntity,
        var entityWithoutAttributeValue: EntityWithoutAttribute) extends Entity

    abstract class ActivateTestDummyEntity(var dummy: Boolean) extends Entity

    // Relational reserved words
    class Order(var key: String) extends Entity

    // Short name entity
    @Alias("sne")
    class ShortNameEntity(var string: String) extends Entity

    class Box(var contains: List[Num] = Nil) extends Entity {
        def add(n: Int) = {
            val num = new Num(this, n)
            contains = num :: contains
            num
        }
    }

    class Num(
        val container: Box,
        var num: Int) extends Entity

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
            var listEntityValue: LazyList[ActivateTestEntity],
            var tupleOptionValue: Option[(Int, Int)]) extends ActivateTestDummyEntity(false) {

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
            emptyTupleOptionValue)
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
            tupleOptionValue = tupleOptionValue)
    }
}