package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.entity.EntityValidationOption._
import net.fwbrasil.activate.util.ManifestUtil._

class String1MustNotBeEmpty extends Exception

class TestValidationEntity(var string1: String) extends Entity {
	var string2 = "s2"
	def string1MustNotBeEmpty =
		invariant(new String1MustNotBeEmpty) {
			string1.nonEmpty
		}
	def string2MustNotBeEmpty =
		invariant {
			string2.nonEmpty
		}
	override protected def validationOptions = TestValidationEntity.validationOptions
}

object TestValidationEntity {
	var validationOptions: Option[Set[EntityValidationOption]] = None
}

@RunWith(classOf[JUnitRunner])
class EntityValidationInvariantsSpecs extends ActivateTest {

	case class EntityValidationStepExecutor(wrapped: StepExecutor, fSetOptions: () => Unit) {
		implicit val ctx = wrapped.ctx
		def apply[A](f: => A) = {
			wrapped {
				EntityValidation.removeAllCustomOptions
				TestValidationEntity.validationOptions = None
				fSetOptions()
				f
			}
		}
	}

	def invariantValidationTest[R](step: StepExecutor)(options: EntityValidationOption*)(f: (EntityValidationStepExecutor) => R) = {
		import step.ctx._
			def run(executor: EntityValidationStepExecutor) = {
				transactional(all[TestValidationEntity].map(_.delete))
				f(executor)
				step.finalizeExecution
			}
		run(new EntityValidationStepExecutor(step, () => EntityValidation.setGlobalOptions(options.toSet)))
		run(new EntityValidationStepExecutor(step, () => EntityValidation.setThreadOptions(options.toSet)))
		run(new EntityValidationStepExecutor(step, () => EntityValidation.setTransactionOptions(options.toSet)))
		run(new EntityValidationStepExecutor(step, () => TestValidationEntity.validationOptions = Some(options.toSet)))
	}

	"Entity validation" should {

		"have onCreate and onWrite as default" in {
			EntityValidation.getGlobalOptions must beEqualTo(Set(onCreate, onWrite))
		}

		"support onCreate & onWrite" in {
			activateTest(invariantValidationTest(_)(onCreate, onWrite)(
				(step: EntityValidationStepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							new TestValidationEntity("") must throwA[String1MustNotBeEmpty]
							new TestValidationEntity("s1").id
						}
						def entity =
							byId[TestValidationEntity](entityId).get
					step {
						all[TestValidationEntity].size must beEqualTo(1)
					}
					step {
						(entity.string1 = "") must throwA[String1MustNotBeEmpty]
						entity.string1 must beEqualTo("s1")
					}
					step {
						(entity.string2 = "") must throwA[InvariantViolationException]
						entity.string2 must beEqualTo("s2")
					}
					step {
						entity.string1 = "ss1"
						entity.string1 must beEqualTo("ss1")
						entity.string2 = "ss2"
						entity.string2 must beEqualTo("ss2")
					}
				}))
		}

		"support onCreate" in {
			activateTest(invariantValidationTest(_)(onCreate)(
				(step: EntityValidationStepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							new TestValidationEntity("") must throwA[String1MustNotBeEmpty]
							new TestValidationEntity("s1").id
						}
						def entity =
							byId[TestValidationEntity](entityId).get
					step {
						all[TestValidationEntity].size must beEqualTo(1)
					}
					step {
						entity.string1 = ""
						entity.string1 must beEqualTo("")
					}
					step {
						entity.string2 = ""
						entity.string2 must beEqualTo("")
					}
					step {
						entity.string1 = "ss1"
						entity.string1 must beEqualTo("ss1")
						entity.string2 = "ss2"
						entity.string2 must beEqualTo("ss2")
					}
				}))
		}

		"support onWrite" in {
			activateTest(invariantValidationTest(_)(onWrite)(
				(step: EntityValidationStepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							new TestValidationEntity("").id
						}
						def entity =
							byId[TestValidationEntity](entityId).get
					step {
						entity.string1 = "s1"
						(entity.string1 = "") must throwA[String1MustNotBeEmpty]
						entity.string1 must beEqualTo("s1")
					}
					step {
						entity.string2 = "s2"
						(entity.string2 = "") must throwA[InvariantViolationException]
						entity.string2 must beEqualTo("s2")
					}
					step {
						entity.string1 = "ss1"
						entity.string1 must beEqualTo("ss1")
						entity.string2 = "ss2"
						entity.string2 must beEqualTo("ss2")
					}
				}))
		}

		"support onRead" in {
			activateTest(invariantValidationTest(_)(onRead)(
				(step: EntityValidationStepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							new TestValidationEntity("").id
						}
						def entity =
							byId[TestValidationEntity](entityId).get
					step {
						val e = entity
						e.string1 must throwA[String1MustNotBeEmpty]
						entity.string1 = "s2"
						entity.string1 must beEqualTo("s2")
						entity.string1 = ""
						entity.string1 must throwA[String1MustNotBeEmpty]
					}
				}))
		}

		"support onRead & onCreate" in {
			activateTest(invariantValidationTest(_)(onRead, onCreate)(
				(step: EntityValidationStepExecutor) => {
					import step.ctx._
					val entityId =
						step {
							new TestValidationEntity("") must throwA[String1MustNotBeEmpty]
							new TestValidationEntity("s1").id
						}
						def entity =
							byId[TestValidationEntity](entityId).get
					step {
						all[TestValidationEntity].size must beEqualTo(1)
					}
					step {
						entity.string1 must beEqualTo("s1")
						entity.string1 = ""
						entity.string1 must throwA[String1MustNotBeEmpty]
						entity.string1 = "s2"
						entity.string1 must beEqualTo("s2")
					}
				}))
		}

			def mustThrowACause[E: Manifest](f: => Unit) = {
				try {
					f
					throw new IllegalStateException("exception wasn't thrown")
				} catch {
					case e =>
						if (e.getCause == null || !erasureOf[E].isAssignableFrom(e.getCause.getClass))
							throw new IllegalStateException("Not the expected cause")
				}
			}

		"support onTransactionEnd" in {
			activateTest(invariantValidationTest(_)(onTransactionEnd)(
				(step: EntityValidationStepExecutor) => {
					if (!step.wrapped.isInstanceOf[OneTransaction]) {
						import step.ctx._
						var erro = false
						mustThrowACause[String1MustNotBeEmpty] {
							step {
								new TestValidationEntity("")
							}
						}
						val entityId =
							step {
								new TestValidationEntity("s1").id
							}
							def entity =
								byId[TestValidationEntity](entityId).get
						step {
							entity.string1 must beEqualTo("s1")
							entity.string1 = ""
							entity.string1 must beEqualTo("")
							entity.string1 = "s2"
							entity.string1 must beEqualTo("s2")
						}
						mustThrowACause[String1MustNotBeEmpty] {
							step {
								entity.string1 = ""
							}
						}
						step {
							entity.string1 must beEqualTo("s2")
						}
					}
				}))
		}

	}

}