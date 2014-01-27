package net.fwbrasil.activate.slick

import org.junit.runner._
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.memoryContext
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.util.ManifestUtil._
import scala.slick.direct.Queryable
import scala.slick.lifted.Tag
import net.fwbrasil.activate.entity.EntityHelper
import scala.slick.jdbc.JdbcBackend
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.statement.StatementMocks
import scala.slick.ast.ScalaBaseType
import scala.slick.ast.ScalaType
import net.fwbrasil.activate.entity._
import scala.slick.ast.TypedType
import scala.reflect.ClassTag
import org.joda.time.DateTime
import java.util.Date
import net.fwbrasil.activate.derbyContext
import net.fwbrasil.activate.h2Context
import net.fwbrasil.activate.hsqldbContext

@RunWith(classOf[JUnitRunner])
class SlickQuerySpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filter(!_.isInstanceOf[OneTransaction])

    override def contexts =
        super.contexts.filter(c => c.isInstanceOf[SlickQueryContext] && c != derbyContext && c != h2Context && c != hsqldbContext)

    "The Slick query" should {
        "support the coffee system" in {
            activateTest(
                (step: StepExecutor) => {
                    val ctx = step.ctx.asInstanceOf[ActivateTestContext with SlickQueryContext]
                    import ctx._
                    import ctx.driver.simple._

                    step {
                        val sup1 = new Supplier("Acme, Inc.", "Groundsville")
                        val sup2 = new Supplier("Superior Coffee", "Mendocino")
                        val sup3 = new Supplier("The High Ground", "Meadows")

                        new Coffee("Colombian", sup1, 7.99)
                        new Coffee("French_Roast", sup2, 8.99)
                        new Coffee("Espresso", sup3, 9.99)
                        new Coffee("Colombian_Decaf", sup1, 8.99)
                        new Coffee("French_Roast_Decaf", sup2, 9.99)
                    }

                    def testCompare[T](slick: Query[_, _], activate: List[_]) =
                        slick.execute === activate

                    def testCompareSet(slick: Query[_, _], activate: List[_]) =
                        slick.execute.toSet === activate.toSet

                    step {

                        testCompareSet(
                            slick =
                                SlickQuery[Coffee].map(_.price.col),
                            activate =
                                query {
                                    (c: Coffee) => where() select (c.price)
                                })

                        testCompare(
                            slick =
                                (for {
                                    c <- SlickQuery[Coffee]
                                    s <- c.supplier.col
                                } yield (c, s)).sortBy(_._1.id.col),
                            activate =
                                query {
                                    (c: Coffee) => where() select (c, c.supplier) orderBy (c.id)
                                })

                        testCompare(
                            slick =
                                (for {
                                    c <- SlickQuery[Coffee]
                                    s <- SlickQuery[Supplier].sortBy(_.id.col) if c.supplier.col === s.col
                                } yield (c.name.col)).sortBy(_.asc).take(2),
                            activate =
                                query {
                                    (c: Coffee) => where() select (c.name) orderBy (c.name) limit (2)
                                })

                        testCompareSet(
                            slick =
                                (for {
                                    c <- SlickQuery[Coffee]
                                    s <- c.supplier.col
                                } yield (s.name.col, c)).groupBy(_._1).map {
                                    case (sname, ts) =>
                                        (sname, ts.length, ts.map(_._2.price.col).min.get)
                                },
                            activate = {
                                all[Coffee]
                                    .groupBy(_.supplier.name)
                                    .map {
                                        tuple =>
                                            val (supName, coffees) = tuple
                                            val prices = coffees.map(_.price)
                                            (supName, prices.size, prices.min)
                                    }.toList
                            })

                    }
                })
        }
    }

}

