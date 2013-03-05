package net.fwbrasil.activate

import net.fwbrasil.activate.storage.prevayler._
import net.fwbrasil.activate.storage.relational._
import net.fwbrasil.activate.storage.memory._
import net.fwbrasil.activate.serialization.javaSerializator
import net.fwbrasil.activate.util.Reflection._
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import org.specs2.execute.FailureException
import scala.runtime._
import java.security._
import java.math.BigInteger
import org.joda.time.DateTime
import net.fwbrasil.activate.storage.mongo.MongoStorage
import net.fwbrasil.radon.util.GCUtil.runGC

import net.fwbrasil.activate.migration.Migration

object runningFlag

trait ActivateTest extends SpecificationWithJUnit with Serializable {

    def executors(ctx: ActivateTestContext): List[StepExecutor] =
        List(
            OneTransaction(ctx),
            MultipleTransactions(ctx),
            MultipleTransactionsWithReinitialize(ctx),
            MultipleTransactionsWithReinitializeAndSnapshot(ctx)).filter(_.accept(ctx))

    def contexts = _contexts

    lazy val _contexts = {
        val ret = List[ActivateTestContext](
            polyglotContext,
            memoryContext,
            prevaylerContext,
            mongoContext,
            mysqlContext,
            postgresqlContext,
            derbyContext,
            h2Context,
            hsqldbContext //,
            //            oracleContext 
            //            db2Context
            )
        ret.foreach(_.stop)
        val db = Option(System.getenv("DB")).getOrElse(System.getProperty("DB"))
        if (db == null)
            ret
        else
            db match {
                case "memoryStorage" =>
                    List(memoryContext)
                case "prevaylerStorage" =>
                    List(prevaylerContext)
                case "mongoStorage" =>
                    List(mongoContext)
                case "postgresStorage" =>
                    List(postgresqlContext)
                case "mysqlStorage" =>
                    List(mysqlContext)
            }
    }

    trait StepExecutor {
        def apply[A](step: => A): A
        def finalizeExecution = {
            //			runGC
        }
        def accept(context: ActivateTestContext) =
            true
        def execute[A](step: => A): A =
            try {
                step
            } catch {
                case e: FailureException =>
                    throw new IllegalStateException(e.f + " (ctx: " + contextName + ", mode: " + modeName + ")", e)
                case e: Throwable =>
                    throw new IllegalStateException(e.getMessage + " (ctx: " + contextName + ", mode: " + modeName + ")", e)
            }
        def contextName = ctx.name
        val modeName = this.niceClass.getSimpleName
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
            super.finalizeExecution
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

    case class MultipleTransactionsWithReinitializeAndSnapshot(ctx: ActivateTestContext) extends StepExecutor {
        import ctx._
        def apply[A](s: => A): A = execute {
            val ret =
                transactional {
                    s
                }
            ctx.storage.asInstanceOf[PrevaylerStorage].snapshot
            reinitializeContext
            ret
        }
        override def accept(ctx: ActivateTestContext) =
            ctx.storage.isInstanceOf[PrevaylerStorage]
    }

    def activateTest[A](f: (StepExecutor) => A) = runningFlag.synchronized {
        for (ctx <- contexts) {
            import ctx._
            start
            try {
                runMigration
                def clear = transactional {
                    all[ActivateTestEntity].foreach(_.delete)
                    all[TraitAttribute].foreach(_.delete)
                    all[EntityWithoutAttribute].foreach(_.delete)
                    all[CaseClassEntity].foreach(_.delete)
                    all[SimpleEntity].foreach(_.delete)
                    all[ShortNameEntity].foreach(_.delete)
                }
                for (executor <- executors(ctx)) {
                    clear
                    f(executor)
                    executor.finalizeExecution
                    clear
                }
            } finally
                stop
        }
        true must beTrue
    }

}