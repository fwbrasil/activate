package net.fwbrasil.activate.slick

//import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.memoryContext
import net.fwbrasil.activate.entity.Entity
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

@RunWith(classOf[JUnitRunner])
class SlickQuerySpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filter(!_.isInstanceOf[OneTransaction])

    override def contexts =
        super.contexts.filter(_.isInstanceOf[SlickQueryContext])

    "The Slick query support" should {
        "perform simple query" in {
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

                    step {

                        val q2 = (for {
                            c <- SlickQuery[Coffee]
                            s <- c.supplier.~
                        } yield (c, s)).sortBy(tuple => EntityValueToColumn(tuple._2.city).~)

                        println(q2.selectStatement)
                        val a = q2.execute
                        println(a)

                        val slickQuery =
                            (for (
                                emptyEntity <- SlickQuery[ActivateTestEntity];
                                fullEntity <- SlickQuery[ActivateTestEntity] if (fullEntity.~ === fullEntity.entityValue.~)
                            ) yield (fullEntity, emptyEntity))
                        val slick = slickQuery.execute
                        val activate =
                            query {
                                (full: ActivateTestEntity, empty: ActivateTestEntity) =>
                                    where(full.entityValue :== empty) select (full, empty)
                            }
                        slick === activate
                    }
                })
        }
        //        "perform query that selects partial values" in {
        //            activateTest(
        //                (step: StepExecutor) => {
        //                    val ctx = step.ctx.asInstanceOf[ActivateTestContext with SlickQueryContext]
        //                    import ctx._
        //                    val entityId =
        //                        step {
        //                            newFullActivateTestEntity.id
        //                        }
        //                    step {
        //                        val q = Queryable[ActivateTestEntity]
        //                        val string = fullStringValue
        //                        val slick = q.filter(_.stringValue == string).map(e => (e.intValue, e.stringValue)).toSeq.toList
        //                        val activate =
        //                            query {
        //                                (e: ActivateTestEntity) => where(e.stringValue :== string) select (e.intValue, e.stringValue)
        //                            }
        //                        slick === activate
        //                    }
        //                })
        //        }
    }

}

