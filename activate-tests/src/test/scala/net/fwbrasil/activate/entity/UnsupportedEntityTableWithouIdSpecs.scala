package net.fwbrasil.activate.entity

import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
import scala.util.Try
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UnsupportedEntityTableWithouIdSpecs extends ActivateTest {

    class Car(var color: String) extends Entity

    override def contexts = super.contexts.filter(_.name == "mysqlContext")

    "Tables without the Activate ID column" should {
        "not produce error with the all query" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val conn = storage.asInstanceOf[JdbcRelationalStorage].directAccess
                    try {
                        conn.setAutoCommit(true)
                        Try(conn.createStatement().executeUpdate("DROP TABLE CAR"))
                        conn.createStatement().executeUpdate("CREATE TABLE CAR(ID BIGINT, COLOR VARCHAR(45))")
                        conn.createStatement().executeUpdate("INSERT INTO CAR VALUES (1, 'BLACK')")
                        transactional {
                            val cars = all[Car]
                            for (car <- cars)
                                println(car.color)
                        } must throwA[IllegalArgumentException]
                    } finally {
                        conn.rollback
                        conn.close
                    }
                })
        }
    }

}