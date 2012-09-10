package net.fwbrasil.activate

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.radon.dsl.actor._

@RunWith(classOf[JUnitRunner])
class ConcurrencySpecs extends ActivateTest {

	override def executors(ctx: ActivateTestContext) =
		List(
			MultipleTransactions(ctx),
			MultipleTransactionsWithReinitialize(ctx),
			MultipleTransactionsWithReinitializeAndSnapshot(ctx)).filter(_.accept(ctx))

	"Activate" should {
		"be consistent" in {
			"concurrent creation" in {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						step {
							new ActorDsl with ManyActors with OneActorPerThread {
								override lazy val actorsPoolSize = 50
								inParallelActors {
									transactional {
										new TraitAttribute1("1")
									}
								}
							}
						}
						step {
							all[TraitAttribute1].size must beEqualTo(50)
							all[TraitAttribute1].map(_.attribute).toSet must beEqualTo(Set("1"))
						}
					})
			}
			"concurrent initialization" in {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						val entityId =
							step {
								val entity = newEmptyActivateTestEntity
								entity.intValue = 1
								entity.id
							}
						step {
							new ActorDsl with ManyActors with OneActorPerThread {
								override lazy val actorsPoolSize = 50
								inParallelActors {
									transactional {
										val entity = byId[ActivateTestEntity](entityId).get
										entity.intValue must beEqualTo(1)
									}
								}
							}
						}
						step {
							all[ActivateTestEntity].onlyOne.intValue must beEqualTo(1)
						}
					})
			}
			"concurrent modification" in {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						val entityId =
							step {
								val entity = newEmptyActivateTestEntity
								entity.intValue = 0
								entity.id
							}
						step {
							val entity = byId[ActivateTestEntity](entityId).get
							entity.intValue must beEqualTo(0)
							new ActorDsl with ManyActors with OneActorPerThread {
								override lazy val actorsPoolSize = 50
								inParallelActors {
									transactional {
										val entity = byId[ActivateTestEntity](entityId).get
										entity.intValue += 1
									}
								}
							}
						}
						step {
							all[ActivateTestEntity].onlyOne.intValue must beEqualTo(50)
						}
					})
			}
			"concurrent initialization and modification" in {
				activateTest(
					(step: StepExecutor) => {
						import step.ctx._
						val entityId =
							step {
								val entity = newEmptyActivateTestEntity
								entity.intValue = 0
								entity.id
							}
						step {
							new ActorDsl with ManyActors with OneActorPerThread {
								override lazy val actorsPoolSize = 50
								inParallelActors {
									transactional {
										val entity = byId[ActivateTestEntity](entityId).get
										entity.intValue += 1
									}
								}
							}
						}
						step {
							all[ActivateTestEntity].onlyOne.intValue must beEqualTo(50)
						}
					})
			}

			//			"concurrent delete" in {
			//				activateTest(
			//					(step: StepExecutor) => {
			//						import step.ctx._
			//						val entityId =
			//							step {
			//								newEmptyActivateTestEntity.id
			//							}
			//							def entityOption =
			//								byId[ActivateTestEntity](entityId)
			//						step {
			//							new ActorDsl with ManyActors with OneActorPerThread {
			//								override lazy val actorsPoolSize = 50
			//								inParallelActors {
			//									transactional {
			//										entityOption.map(entity => if (!entity.isDeleted) entity.delete)
			//									}
			//								}
			//							}
			//						}
			//						step {
			//							all[ActivateTestEntity] must beEmpty
			//							entityOption must beEmpty
			//						}
			//					})
			//			}
		}
	}
}