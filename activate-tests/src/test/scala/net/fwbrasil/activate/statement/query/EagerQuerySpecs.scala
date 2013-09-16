package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.derbyContext

@RunWith(classOf[JUnitRunner])
class EagerQuerySpecs extends ActivateTest {

    override def contexts = super.contexts.filter(c => c.storage.supportsQueryJoin && c != derbyContext)

    override def executors(ctx: ActivateTestContext): List[StepExecutor] =
        List(MultipleTransactionsWithReinitialize(ctx))

    "Eager queries" should {

        //        "initialize eager entities with the query result" in {
        //            activateTest(
        //                (step: StepExecutor) => {
        //                    import step.ctx._
        //                    step {
        //                        newFullActivateTestEntity
        //                    }
        //                    step {
        //                        val (a, b) =
        //                            query {
        //                                (a: ActivateTestEntity, b: ActivateTestEntity) =>
        //                                    where((a.entityValue :== b) :&& (a.intValue :== fullIntValue)) select (a.eager, b)
        //                            }.head
        //
        //                        a.isInitialized must beTrue
        //                        if (storage.isMemoryStorage)
        //                            b.isInitialized must beTrue
        //                        else
        //                            b.isInitialized must beFalse
        //                    }
        //                    step {
        //                        val (a, b) =
        //                            query {
        //                                (a: ActivateTestEntity, b: ActivateTestEntity) =>
        //                                    where((a.entityValue :== b) :&& (a.intValue :== fullIntValue)) select (a, b.eager)
        //                            }.head
        //
        //                        b.isInitialized must beTrue
        //                        if (storage.isMemoryStorage)
        //                            a.isInitialized must beTrue
        //                        else
        //                            a.isInitialized must beFalse
        //                    }
        //                    step {
        //                        val (a, b) =
        //                            query {
        //                                (a: ActivateTestEntity, b: ActivateTestEntity) =>
        //                                    where((a.entityValue :== b) :&& (a.intValue :== fullIntValue)) select (a.eager, b.eager)
        //                            }.head
        //
        //                        a.isInitialized must beTrue
        //                        b.isInitialized must beTrue
        //                    }
        //                })
        //        }

        "support eager nested entity" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newFullActivateTestEntity
                    }
                    step {
                        val res =
                            query {
                                (e: ActivateTestEntity) =>
                                    where(e.intValue :== fullIntValue) select (e.traitValue1.eager)
                            }

                        query {
                            (e: ActivateTestEntity) =>
                                where(e.intValue :== fullIntValue) select (e.entityValue.eager)
                        }
                        println(res)
                    }
                })
        }
    }

}