package net.fwbrasil.activate.statement.query

import java.util.Date
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.radon.dsl.actor._
import net.fwbrasil.activate.ActivateTestContext

@RunWith(classOf[JUnitRunner])
class QueryCacheSpecs extends ActivateTest {

	override def executors(ctx: ActivateTestContext) =
		List(
			MultipleTransactions(ctx),
			MultipleTransactionsWithReinitialize(ctx),
			MultipleTransactionsWithReinitializeAndSnapshot(ctx)).filter(_.accept(ctx))

	"Query cache" should {
		"support multiple threads" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							newEmptyActivateTestEntity.id
						}
					step {
						new ActorDsl with ManyActors with OneActorPerThread {
							override lazy val actorsPoolSize = 5
							inParallelActors {
								val result =
									transactional {
										query {
											(e: ActivateTestEntity) => where(e isNotNull) select (e)
										}
									}
								result.onlyOne.id must beEqualTo(entityId)
							}
						}
					}
				})
		}
		"be consistent with parameters" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						new TraitAttribute1("1")
						new TraitAttribute2("2")
					}
					step {
							def testQuery(attribute: String) =
								query {
									(t: TraitAttribute) => where(t.attribute :== attribute) select (t)
								}
						testQuery("1").onlyOne.attribute must beEqualTo("1")
						testQuery("2").onlyOne.attribute must beEqualTo("2")
					}
				})
		}
		"be consistent with manifests parameters" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						new TraitAttribute1("1")
						new TraitAttribute2("2")
					}
					step {
							def testQuery(clazz: Class[_]) =
								query {
									(t: TraitAttribute) => where(t isNotNull) select (t)
								}(manifestClass(clazz))
						testQuery(classOf[TraitAttribute1]).onlyOne.attribute must beEqualTo("1")
						testQuery(classOf[TraitAttribute2]).onlyOne.attribute must beEqualTo("2")
					}
				})
		}
	}
}