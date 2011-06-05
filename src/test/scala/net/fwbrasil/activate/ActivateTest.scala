package net.fwbrasil.activate

import net.fwbrasil.activate.storage.memory._
import net.fwbrasil.activate.storage.relational._
import net.fwbrasil.activate.storage.map.cassandra._
import net.fwbrasil.activate.serialization.javaSerializator
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import org.specs2.execute.FailureException
import tools.scalap.scalax.rules.scalasig._
import scala.runtime._

object runningFlag

@RunWith(classOf[JUnitRunner])
trait ActivateTest extends Specification {

	trait StepExecutor {
		def apply[A](step: => A): A
		def finalizeExecution = {

		}
		def execute[A](step: => A): A =
			try {
				step
			} catch {
				case e: FailureException =>
					throw new IllegalStateException(e.f + " (ctx: " + contextName + ", mode: " + modeName + ")", e)
				case e =>
					throw new IllegalStateException(e.getMessage + " (ctx: " + contextName + ", mode: " + modeName + ")", e)
			}
		def contextName = ctx.name
		val modeName = this.getClass.getSimpleName
		val ctx: ActivateTestContext
	}

	case class OneTransaction(ctx: ActivateTestContext) extends StepExecutor {
		import ctx._
		val transaction = new Transaction
		def apply[A](s: => A): A = execute {
			transactional(transaction) {
				s
			}
		}
		override def finalizeExecution = {
			transaction.commit
		}
	}

	case class MultipleTransactions(ctx: ActivateTestContext) extends StepExecutor {
		import ctx._
		def apply[A](s: => A): A = execute {
			transactional {
				s
			}
		}
	}

	case class MultipleTransactionsWithReinitialize(ctx: ActivateTestContext) extends StepExecutor {
		import ctx._
		def apply[A](s: => A): A = execute {
			val ret =
				transactional {
					s
				}
			reinitializeContext
			ret
		}
	}

	def executors(ctx: ActivateTestContext) =
		List(OneTransaction(ctx), MultipleTransactions(ctx), MultipleTransactionsWithReinitialize(ctx))

	def activateTest[A](f: (StepExecutor) => A) = runningFlag.synchronized {
		for (ctx <- contexts) {
			import ctx._
			def clear = transactional {
				all[ActivateTestEntity].foreach(_.delete)
			}
			for (executor <- executors(ctx)) {
				clear
				f(executor)
				executor.finalizeExecution
				clear
			}
		}
		true must beTrue
	}

	trait ActivateTestContext extends ActivateContext {
		
		override val retryLimit = 2
		
		def contextName =
			this.getClass.getSimpleName

		val fullIntValue = Option(1)
		val fullBooleanValue = Option(true)
		val fullCharValue = Option('A')
		val fullStringValue = Option("S")
		val fullFloatValue = Option(0.1f)
		val fullDoubleValue = Option(1d)
		val fullBigDecimalValue = Option(BigDecimal(1))
		val fullDateValue = Option(new java.util.Date(98977898))
		val fullCalendarValue = Option({
			val cal = java.util.Calendar.getInstance()
			cal.setTimeInMillis(98977898)
			cal
		})
		val fullByteArrayValue = Option("S".getBytes)
		def fullEntityValue =
			Option(allWhere[ActivateTestEntity](_.dummy :== true).headOption.getOrElse({
				val entity = newEmptyActivateTestEntity
				entity.dummy := true
				entity
			}))

		def setFullEntity(entity: ActivateTestEntity) = {
			entity.intValue.put(fullIntValue)
			entity.booleanValue.put(fullBooleanValue)
			entity.charValue.put(fullCharValue)
			entity.stringValue.put(fullStringValue)
			entity.floatValue.put(fullFloatValue)
			entity.doubleValue.put(fullDoubleValue)
			entity.bigDecimalValue.put(fullBigDecimalValue)
			entity.dateValue.put(fullDateValue)
			entity.calendarValue.put(fullCalendarValue)
			entity.byteArrayValue.put(fullByteArrayValue)
			entity.entityValue.put(fullEntityValue)
			entity
		}

		def setEmptyEntity(entity: ActivateTestEntity) = {
			entity.intValue.put(None)
			entity.booleanValue.put(None)
			entity.charValue.put(None)
			entity.stringValue.put(None)
			entity.floatValue.put(None)
			entity.doubleValue.put(None)
			entity.bigDecimalValue.put(None)
			entity.dateValue.put(None)
			entity.calendarValue.put(None)
			entity.byteArrayValue.put(None)
			entity.entityValue.put(None)
			entity
		}

		def newEmptyActivateTestEntity =
			setEmptyEntity(newTestEntity())
		def newFullActivateTestEntity =
			setFullEntity(newTestEntity())
		case class ActivateTestEntity(
			dummy: Var[Boolean] = false,
			intValue: Var[Int],
			booleanValue: Var[Boolean],
			charValue: Var[Char],
			stringValue: Var[String],
			floatValue: Var[Float],
			doubleValue: Var[Double],
			bigDecimalValue: Var[BigDecimal],
			dateValue: Var[java.util.Date],
			calendarValue: Var[java.util.Calendar],
			byteArrayValue: Var[Array[Byte]],
			entityValue: Var[ActivateTestEntity]) extends Entity

		def validateFullTestEntity(entity: ActivateTestEntity = null,
			intValue: Option[Int] = fullIntValue,
			booleanValue: Option[Boolean] = fullBooleanValue,
			charValue: Option[Char] = fullCharValue,
			stringValue: Option[String] = fullStringValue,
			floatValue: Option[Float] = fullFloatValue,
			doubleValue: Option[Double] = fullDoubleValue,
			bigDecimalValue: Option[BigDecimal] = fullBigDecimalValue,
			dateValue: Option[java.util.Date] = fullDateValue,
			calendarValue: Option[java.util.Calendar] = fullCalendarValue,
			byteArrayValue: Option[Array[Byte]] = fullByteArrayValue,
			entityValue: Option[ActivateTestEntity] = fullEntityValue) =

			validateEmptyTestEntity(
				entity,
				intValue,
				booleanValue,
				charValue,
				stringValue,
				floatValue,
				doubleValue,
				bigDecimalValue,
				dateValue,
				calendarValue,
				byteArrayValue,
				entityValue)

		def validateEmptyTestEntity(entity: ActivateTestEntity = null,
			intValue: Option[Int] = None,
			booleanValue: Option[Boolean] = None,
			charValue: Option[Char] = None,
			stringValue: Option[String] = None,
			floatValue: Option[Float] = None,
			doubleValue: Option[Double] = None,
			bigDecimalValue: Option[BigDecimal] = None,
			dateValue: Option[java.util.Date] = None,
			calendarValue: Option[java.util.Calendar] = None,
			byteArrayValue: Option[Array[Byte]] = None,
			entityValue: Option[ActivateTestEntity] = None) = {

			entity.intValue.get must beEqualTo(intValue)
			entity.booleanValue.get must beEqualTo(booleanValue)
			entity.charValue.get must beEqualTo(charValue)
			entity.stringValue.get must beEqualTo(stringValue)
			entity.floatValue.get must beEqualTo(floatValue)
			entity.doubleValue.get must beEqualTo(doubleValue)
			entity.bigDecimalValue.get must beEqualTo(bigDecimalValue)
			entity.dateValue.get must beEqualTo(dateValue)
			entity.calendarValue.get must beEqualTo(calendarValue)
			entity.entityValue.get must beEqualTo(entityValue)
		}

		def newTestEntity(intValue: Option[Int] = None,
			booleanValue: Option[Boolean] = None,
			charValue: Option[Char] = None,
			stringValue: Option[String] = None,
			floatValue: Option[Float] = None,
			doubleValue: Option[Double] = None,
			bigDecimalValue: Option[BigDecimal] = None,
			dateValue: Option[java.util.Date] = None,
			calendarValue: Option[java.util.Calendar] = None,
			byteArrayValue: Option[Array[Byte]] = None,
			entityValue: Option[ActivateTestEntity] = None) =
			new ActivateTestEntity(
				intValue = intValue,
				booleanValue = booleanValue,
				charValue = charValue,
				stringValue = stringValue,
				floatValue = floatValue,
				doubleValue = doubleValue,
				bigDecimalValue = bigDecimalValue,
				dateValue = dateValue,
				calendarValue = calendarValue,
				byteArrayValue = byteArrayValue,
				entityValue = entityValue)
		def createEmptyAndFullEntity = {
			transactional {
				val empty = newEmptyActivateTestEntity
				empty.intValue := 1
				val full = newFullActivateTestEntity
				full.intValue := 0
				(empty.id, full.id)
			}
		}
	}

	object prevaylerContext extends ActivateTestContext {
		val storage = new PrevaylerMemoryStorage {
			override lazy val name = "test/PrevalenceBase/testPrevaylerMemoryStorage" + (new java.util.Date).getTime
		}
	}

	object memoryContext extends ActivateTestContext {
		val storage = new MemoryStorage {}
	}

	object oracleContext extends ActivateTestContext {
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
			val user = "ACTIVATE"
			val password = "ACTIVATE"
			val url = "jdbc:oracle:thin:@localhost:1521:oracle"
			val dialect = oracleDialect
			val serializator = javaSerializator
		}
	}

	object mysqlContext extends ActivateTestContext {
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "com.mysql.jdbc.Driver"
			val user = "root"
			val password = "root"
			val url = "jdbc:mysql://127.0.0.1/teste"
			val dialect = mySqlDialect
			val serializator = javaSerializator
		}
	}

	def contexts =
		List[ActivateTestContext](
			prevaylerContext,
			memoryContext,
			oracleContext,
			mysqlContext
		)
}