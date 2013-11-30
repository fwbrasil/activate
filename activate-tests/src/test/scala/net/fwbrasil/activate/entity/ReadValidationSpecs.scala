package net.fwbrasil.activate.entity

import scala.concurrent.duration.DurationInt
import org.junit.runner.RunWith
import org.specs2.mutable.SpecificationWithJUnit
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.multipleVms.JvmFork
import net.fwbrasil.activate.postgresqlContext
import org.joda.time.DateTimeUtils
import org.joda.time.DateTime
import scala.concurrent.duration._

object readValidationContext extends ActivateContext {
    object versionMigration extends ManualMigration {
        def up = {
            removeAllEntitiesTables.cascade.ifExists
            createTableForAllEntities.ifNotExists
            table[ReadValidationEntity]
                .addColumn(_.column[Long]("version"))
                .ifNotExists
        }
    }

    lazy val storage = postgresqlContext.storage

    versionMigration.execute
}

class ReadValidationEntity(var intValue: Int) extends Entity {
    override def shouldValidateRead = ReadValidationEntity.shouldValidateRead()
}

object ReadValidationEntity {
    var shouldValidateRead: () => Boolean = () => true
}

import readValidationContext._

@RunWith(classOf[JUnitRunner])
class ReadValidationSpecs extends SpecificationWithJUnit with Serializable {

    override def intToRichLong(v: Int) = ???

    args.execute(threadsNb = 1)

    val optimisticOfflineLockingOption = "-Dactivate.offlineLocking.enable=true"
    val optimisticOfflineLockingValidateReadOption = "-Dactivate.offlineLocking.validateReads=true"

    "The read validation" should {

        "maintain the lastVersionValidation" in {

            "on create" in fork {
                setTime(1)
                val entity = transactional(new ReadValidationEntity(0))
                require(entity.lastVersionValidation.equals(DateTime.now))
            }

            "on update" in fork {
                setTime(1)
                val entity = transactional(new ReadValidationEntity(0))
                setTime(2)
                transactional(entity.intValue = 2)
                require(entity.lastVersionValidation.equals(DateTime.now))
            }

            "on delete" in fork {
                setTime(1)
                val entity = transactional(new ReadValidationEntity(0))
                setTime(2)
                transactional(entity.delete)
                require(entity.lastVersionValidation.equals(DateTime.now))
            }

            "on read validation" in fork {
                setTime(1)
                val entity = transactional(new ReadValidationEntity(0))
                setTime(2)
                ReadValidationEntity.shouldValidateRead = () => true
                transactional(entity.intValue)
                require(entity.lastVersionValidation.equals(DateTime.now))
            }
        }

        "defer read validation for 1 milis" in fork {
            setTime(1)
            val entity = transactional(new ReadValidationEntity(0))
            deferReadValidationFor(1 millis, entity)
            require(!entity.shouldValidateRead)
            setTime(2)
            require(entity.shouldValidateRead)
        }

        "defer read validation for the infinite" in fork {
            setTime(1)
            val entity = transactional(new ReadValidationEntity(0))
            deferReadValidationFor(Duration.Inf, entity)
            require(!entity.shouldValidateRead)
            setTime(2000)
            require(!entity.shouldValidateRead)
        }

        "reload the entity if necessary" in fork {
            val oldValue = 0
            val newValue = 1
            setTime(1)
            val entity = transactional(new ReadValidationEntity(0))
            ReadValidationEntity.shouldValidateRead = () => true
            modifyEntityOnDatabase(entity.id, newValue)
            require(transactional(entity.intValue) == newValue)
        }

        "reload the entity if necessary after the deferred read time" in fork {
            val oldValue = 0
            val newValue = 1
            setTime(1)
            val entity = transactional(new ReadValidationEntity(0))
            val entityId = entity.id
            modifyEntityOnDatabase(entity.id, newValue)
            deferReadValidationFor(1 millis, entity)
            require(transactional(entity.intValue) == oldValue)
            setTime(2)
            require(transactional(entity.intValue) == newValue)
        }
    }

    def modifyEntityOnDatabase(entityId: String, value: Int) = {
        val con = readValidationContext.storage.directAccess
        con.prepareStatement(s"UPDATE ReadValidationEntity SET version = version + 1, intValue = $value WHERE ID = '$entityId'")
            .executeUpdate
        con.commit
        con.close
    }

    def deferReadValidationFor(duration: Duration, entity: BaseEntity) =
        ReadValidationEntity.shouldValidateRead = () => BaseEntity.deferReadValidationFor(duration, entity)

    def setTime(milis: Long) =
        DateTimeUtils.setCurrentMillisFixed(milis)

    def fork[R: Manifest](f: => R) = {
        JvmFork.forkAndExpect(others = List(optimisticOfflineLockingOption, optimisticOfflineLockingValidateReadOption)) {
            f
            1
        }
        ok
    }
}