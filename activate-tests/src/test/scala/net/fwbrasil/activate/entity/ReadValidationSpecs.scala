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

class ReadValidationEntity(var intValue: Int) extends Entity with UUID {
    override def shouldValidateRead = ReadValidationEntity.shouldValidateRead()
}

object ReadValidationEntity {
    var shouldValidateRead: () => Boolean = () => true
}

import readValidationContext._

@RunWith(classOf[JUnitRunner])
class ReadValidationSpecs extends SpecificationWithJUnit with Serializable {

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
        
        "perform deferred read validation" in fork {
            setTime(1)
            val entity = transactional(new ReadValidationEntity(0))
            ReadValidationEntity.shouldValidateRead = () => Entity.deferReadValidationFor(10l, entity)
        }
    }

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