package net.fwbrasil.activate

import net.fwbrasil.activate.storage.prevayler._
import net.fwbrasil.activate.storage.relational._
import net.fwbrasil.activate.storage.memory._
import net.fwbrasil.activate.serialization.jsonSerializer
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
import net.fwbrasil.activate.migration.Migration
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import javax.swing.JOptionPane
import javax.swing.JFrame
import javax.swing.JList
import java.util.prefs.Preferences
import net.fwbrasil.activate.entity.TestValidationEntity
import net.fwbrasil.radon.ConcurrentTransactionException

object runningFlag

object ActivateTestContextCategory extends Enumeration {
    type ActivateTestContextCategory = Value
    val memory = Value("memory")
    val prevalent = Value("prevalent")
    val mongo = Value("mongo")
    //    val cassandra = Value("cassandra")
    val relational = Value("relational")
    val proprietary = Value("proprietary")
    val polyglot = Value("polyglot")
}

object ActivateTest {
    import ActivateTestContextCategory._

    val contextsCategoriesMap =
        Map[ActivateTestContextCategory, List[ActivateTestContext]](
            memory -> List(memoryContext),
            prevalent -> List(prevaylerContext, prevalentContext),
            mongo -> List(mongoContext, asyncMongoContext),
            relational -> List(postgresqlContext, asyncPostgresqlContext,
                mysqlContext, /*derbyContext,*/ h2Context, hsqldbContext),
            proprietary -> List(oracleContext, db2Context, sqlServerContext),
            //            cassandra -> List(asyncCassandraContext),
            polyglot -> List(polyglotContext))

    val allContexts = contextsCategoriesMap.values.flatten

    val contexts: List[ActivateTestContext] = {

        val contexts =
            propertyOption("context")
                .map(contextsByName)
                .getOrElse {
                    val selectedCategories =
                        propertyOption("category")
                            .map(categoriesFromString)
                            .getOrElse(askForCategories)
                    val selected =
                        contextsCategoriesMap.filterKeys(c =>
                            selectedCategories.contains(c.toString))
                    selected.values.flatten.toList
                }
        contexts.foreach(_.stop)
        contexts
    }

    private def propertyOption(name: String) =
        Option(System.getenv(name))
            .orElse(Option(System.getProperty(name)))

    private def contextsByName(name: String) =
        allContexts.filter(_.name == name).toList

    private def categoriesFromString(string: String) =
        if (string == "ask" || string.isEmpty())
            askForCategories
        else
            string.split(',')

    private def askForCategories = {
        import scala.collection.JavaConversions._
        val values = ActivateTestContextCategory.values.map(_.toString).toArray[Object]
        val list = new JList(values)
        list.setSelectedIndices(categoriesIndicesFromPreferences)
        JOptionPane.showMessageDialog(
            null, list, "Select turn", JOptionPane.PLAIN_MESSAGE)
        val indices = list.getSelectedIndices
        setCategoriesIndicesToPreferences(indices)
        indices.map(values(_).toString)
    }

    def prefs = Preferences.userNodeForPackage(this.getClass);

    private def categoriesIndicesFromPreferences =
        prefs.get("categories", "").split(',').filter(_.nonEmpty).map(_.toInt)

    private def setCategoriesIndicesToPreferences(indices: Array[Int]) = {
        prefs.put("categories", indices.mkString(","))
    }

}

trait ActivateTest extends SpecificationWithJUnit with Serializable {

    args.execute(threadsNb = 1)

    def executors(ctx: ActivateTestContext): List[StepExecutor] =
        List(
            OneTransaction(ctx),
            MultipleTransactions(ctx),
            MultipleAsyncTransactions(ctx),
            MultipleTransactionsWithReinitialize(ctx)).filter(_.accept(ctx))

    def contexts = ActivateTest.contexts

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
        implicit val ctx: ActivateTestContext
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

    case class MultipleAsyncTransactions(ctx: ActivateTestContext) extends StepExecutor {
        import ctx._
        def apply[A](s: => A): A = execute {
            val f =
                asyncTransactional {
                    s
                }
            Await.result(f, Duration.Inf)
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
        override def accept(ctx: ActivateTestContext) =
            !ctx.storage.isInstanceOf[BasePrevalentStorage[_, _]]
    }

    case class MultipleAsyncTransactionsWithReinitialize(ctx: ActivateTestContext) extends StepExecutor {
        import ctx._
        def apply[A](s: => A): A = execute {
            val f =
                asyncTransactional {
                    s
                }
            val ret = Await.result(f, Duration.Inf)
            reinitializeContext
            ret
        }
        override def accept(ctx: ActivateTestContext) =
            ctx.storage.supportsAsync
    }

    case class MultipleTransactionsWithReinitializeAndSnapshot(ctx: ActivateTestContext) extends StepExecutor {
        import ctx._
        def apply[A](s: => A): A = execute {
            val ret =
                transactional {
                    s
                }
            ctx.storage match {
                case storage: BasePrevalentStorage[_, _] =>
                    storage.snapshot
                case other =>
            }
            reinitializeContext
            ret
        }
        override def accept(ctx: ActivateTestContext) =
            ctx.storage.isInstanceOf[BasePrevalentStorage[_, _]]
    }

    def activateTest[A](f: (StepExecutor) => A) = runningFlag.synchronized {
        for (ctx <- contexts) {
            import ctx._
            start
            try {
                runMigration
                def clear = transactional {
                    all[TestValidationEntity].foreach(_.delete)
                    all[ActivateTestEntity].foreach(_.delete)
                    all[TraitAttribute].foreach(_.delete)
                    all[EntityWithoutAttribute].foreach(_.delete)
                    all[CaseClassEntity].foreach(_.delete)
                    all[SimpleEntity].foreach(_.delete)
                    all[ShortNameEntity].foreach(_.delete)
                }
                val executors = this.executors(ctx)
                for (executor <- executors)
                    runWithRetry {
                        clear
                        f(executor)
                        executor.finalizeExecution
                        clear
                    }
            } finally
                stop
        }
        ok
    }

    private def runWithRetry(f: => Unit): Unit = {
        try f
        catch {
            case e: ConcurrentTransactionException =>
                runWithRetry(f)
        }
    }

}