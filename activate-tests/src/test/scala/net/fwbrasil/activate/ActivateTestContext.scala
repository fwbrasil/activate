package net.fwbrasil.activate

import net.fwbrasil.activate.util.Reflection.toNiceObject
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

object EnumerationValue extends Enumeration {
	case class EnumerationValue(name: String) extends Val(name)
	val value1a = EnumerationValue("v1")
	val value2 = EnumerationValue("v2")
	val value3 = EnumerationValue("v3")
}
import EnumerationValue._

case class DummySeriablizable(val string: String)

abstract class ActivateTestMigration(
	implicit val ctx: ActivateTestContext)
		extends Migration {

	val timestamp = System.currentTimeMillis
	val name = "ActivateTestMigration"
	override val developers = List("fwbrasil")

	def up = {

		// Cascade option is ignored in MySql and Derby
		if (ctx == mysqlContext || ctx == derbyContext)
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

	val timestamp = System.currentTimeMillis + 100000
	val name = "ActivateTestMigrationCustomColumnType"
	override val developers = List("fwbrasil")

	def up = {
		table[ctx.ActivateTestEntity].removeColumn("bigStringValue")
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

object oracleContext extends ActivateTestContext {
	val storage = new PooledJdbcRelationalStorage {
		val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
		val user = "ACTIVATE_TEST"
		val password = "ACTIVATE_TEST"
		val url = "jdbc:oracle:thin:@10.211.55.3:1521:oracle"
		val dialect = oracleDialect
	}
}
class OracleActivateTestMigration extends ActivateTestMigration()(oracleContext)
class OracleActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(oracleContext) {
	override def bigStringType = "CLOB"
}

object mysqlContext extends ActivateTestContext {
	System.getProperties.put("activate.storage.mysql.factory", "net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorageFactory")
	System.getProperties.put("activate.storage.mysql.jdbcDriver", "com.mysql.jdbc.Driver")
	System.getProperties.put("activate.storage.mysql.user", "root")
	System.getProperties.put("activate.storage.mysql.password", "")
	System.getProperties.put("activate.storage.mysql.url", "jdbc:mysql://127.0.0.1/activate_test")
	System.getProperties.put("activate.storage.mysql.dialect", "mySqlDialect")
	val storage =
		StorageFactory.fromSystemProperties("mysql")
}
class MysqlActivateTestMigration extends ActivateTestMigration()(mysqlContext)
class MysqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(mysqlContext) {
	override def bigStringType = "TEXT"
}

object postgresqlContext extends ActivateTestContext {
	val storage = new PooledJdbcRelationalStorage {
		val jdbcDriver = "org.postgresql.Driver"
		val user = "postgres"
		val password = ""
		val url = "jdbc:postgresql://127.0.0.1/activate_test"
		val dialect = postgresqlDialect
	}
}
class PostgresqlActivateTestMigration extends ActivateTestMigration()(postgresqlContext)
class PostgresqlActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(postgresqlContext) {
	override def bigStringType = "TEXT"
}

object h2Context extends ActivateTestContext {
	val storage = new SimpleJdbcRelationalStorage {
		val jdbcDriver = "org.h2.Driver"
		val user = "sa"
		val password = ""
		val url = "jdbc:h2:mem:activate_test2;DB_CLOSE_DELAY=-1"
		val dialect = h2Dialect
	}
	val permanentConnectionToHoldMemoryDatabase = storage.getConnection
}

class H2ActivateTestMigration extends ActivateTestMigration()(h2Context)
class H2ActivateTestMigrationCustomColumnType extends ActivateTestMigrationCustomColumnType()(h2Context) {
	override def bigStringType = "CLOB"
}

object derbyContext extends ActivateTestContext {
	System.setProperty("derby.locks.deadlockTrace", "true")
	val storage = new PooledJdbcRelationalStorage {
		val jdbcDriver = "org.apache.derby.jdbc.EmbeddedDriver"
		val user = ""
		val password = ""
		val url = "jdbc:derby:activate_test11;create=true"
		val dialect = derbyDialect
	}
	val permanentConnectionToHoldMemoryDatabase = storage.getConnection
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

object BigStringGenerator {
	val generated = {
		var s = ""
		for (i <- 0 until 400)
			s += "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789"
		s
	}

}

trait ActivateTestContext extends ActivateContext {

	//	override val retryLimit = 2

	private[this] var running = false

	private[activate] def start = synchronized {
		running = true
	}

	private[activate] def stop = synchronized {
		running = false
	}

	override protected[activate] def acceptEntity[E <: Entity](entityClass: Class[E]) =
		running

	override val contextEntities = Some(List(classOf[ActivateTestEntity]))

	override protected lazy val runMigrationAtStartup = false

	override protected[activate] def entityMaterialized(entity: Entity) =
		if (entity.getClass.getDeclaringClass == classOf[ActivateTestContext])
			Reflection.set(entity, "$outer", this)

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
	val emptyEntityWithoutAttributeValue = null
	val emptyCaseClassEntityValue = null
	val emptySerializableEntityValue = null

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
		allWhere[ActivateTestEntity](_.dummy :== true).headOption.getOrElse({
			val entity = newEmptyActivateTestEntity
			entity.dummy = true
			entity
		})

	val fullJodaInstantValue = new DateTime(78317811l)
	val fullOptionValue = Some("string")
	def fullEntityWithoutAttributeValue =
		all[EntityWithoutAttribute].headOption.getOrElse(
			new EntityWithoutAttribute)

	def fullCaseClassEntityValue =
		all[CaseClassEntity].headOption.getOrElse(
			new CaseClassEntity(fullStringValue, fullEntityValue, fullEntityWithoutAttributeValue))

	val fullSerializableEntityValue =
		new DummySeriablizable("dummy")

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
		entity.entityWithoutAttributeValue = fullEntityWithoutAttributeValue
		entity.caseClassEntityValue = fullCaseClassEntityValue
		entity.serializableEntityValue = fullSerializableEntityValue
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
		entity.entityWithoutAttributeValue = emptyEntityWithoutAttributeValue
		entity.caseClassEntityValue = emptyCaseClassEntityValue
		entity.serializableEntityValue = emptySerializableEntityValue
		entity
	}

	def newEmptyActivateTestEntity =
		setEmptyEntity(newTestEntity())
	def newFullActivateTestEntity =
		setFullEntity(newTestEntity())

	trait TraitAttribute extends Entity {
		def attribute: String
	}

	class TraitAttribute1 private (var attribute: String, val dummy: String) extends TraitAttribute {
		def this(attribute: String) =
			this(attribute, attribute)
		def testTraitAttribute = attribute
	}

	class TraitAttribute2(val attribute: String) extends TraitAttribute {
		def testTraitAttribute = attribute
	}

	class Employee(var name: String, var supervisor: Option[Employee]) extends Entity {
		def subordinates = select[Employee] where (_.supervisor :== Some(this))
	}

	class EntityWithoutAttribute extends Entity

	case class CaseClassEntity(
		var stringValue: String,
		var entityValue: ActivateTestEntity,
		var entityWithoutAttributeValue: EntityWithoutAttribute) extends Entity

	abstract class ActivateTestDummyEntity(var dummy: Boolean) extends Entity

	// Relational reserved words
	class Order(val key: String) extends Entity

	// Short name entity
	@EntityName("sne")
	class ShortNameEntity(var string: String) extends Entity

	class ActivateTestEntity(
			dummy: Boolean = false,
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
			var entityWithoutAttributeValue: EntityWithoutAttribute,
			var caseClassEntityValue: CaseClassEntity,
			var serializableEntityValue: DummySeriablizable) extends ActivateTestDummyEntity(dummy) {

		def this(intValue: Int) = this(
			false,
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
			emptyEntityWithoutAttributeValue,
			emptyCaseClassEntityValue,
			emptySerializableEntityValue)
		lazy val lazyValue: String = lazyValueValue
		var varInitializedInConstructor = fullStringValue
		val valInitializedInConstructor = fullStringValue
		val calculatedInConstructor = intValue * 2
		var bigStringValue = BigStringGenerator.generated
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
		lazyValue: String = fullLazyValue,
		optionValue: Option[String] = fullOptionValue,
		entityWithoutAttributeValue: EntityWithoutAttribute = fullEntityWithoutAttributeValue,
		caseClassEntityValue: CaseClassEntity = fullCaseClassEntityValue,
		serializableEntityValue: DummySeriablizable = fullSerializableEntityValue) =

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
			lazyValue,
			optionValue,
			entityWithoutAttributeValue,
			caseClassEntityValue,
			serializableEntityValue)

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
		lazyValue: String = emptyLazyValue,
		optionValue: Option[String] = emptyOptionValue,
		entityWithoutAttributeValue: EntityWithoutAttribute = emptyEntityWithoutAttributeValue,
		caseClassEntityValue: CaseClassEntity = emptyCaseClassEntityValue,
		serializableEntityValue: DummySeriablizable = emptySerializableEntityValue) = {

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
		require(entity.optionValue == optionValue)
		require(entity.entityWithoutAttributeValue == entityWithoutAttributeValue)
		require(entity.serializableEntityValue == serializableEntityValue)
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
		entityWithoutAttributeValue: EntityWithoutAttribute = emptyEntityWithoutAttributeValue,
		caseClassEntityValue: CaseClassEntity = emptyCaseClassEntityValue,
		serializableEntityValue: DummySeriablizable = emptySerializableEntityValue) = {
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
			entityWithoutAttributeValue = entityWithoutAttributeValue,
			caseClassEntityValue = caseClassEntityValue,
			serializableEntityValue = serializableEntityValue)
	}
}