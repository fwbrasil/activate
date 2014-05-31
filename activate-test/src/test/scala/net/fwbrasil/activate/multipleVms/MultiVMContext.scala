package net.fwbrasil.activate.multipleVms

import net.fwbrasil.activate.StoppableActivateContext
import net.fwbrasil.activate.postgresqlContext
import net.fwbrasil.activate.migration.ManualMigration
import net.fwbrasil.activate.mongoContext
import net.fwbrasil.activate.asyncMongoContext
import net.fwbrasil.activate.asyncFinagleMysqlContext

trait MultiVMContext extends StoppableActivateContext {

    override val milisToWaitBeforeRetry = 0
    override val retryLimit = Int.MaxValue

    class IntEntity extends Entity {
        var intValue = 0
    }
    
    object versionMigration extends ManualMigration {
        def up =
            table[IntEntity]
                .addColumn(_.column[Long]("version"))
                .ifNotExists
    }

    lazy val storage = asyncFinagleMysqlContext.storage
    
    versionMigration.execute
    
    def run[A](f: => A) = {
        start
        try
            transactional(f)
        finally
            stop
    }

}

object ctx1 extends MultiVMContext
object ctx2 extends MultiVMContext