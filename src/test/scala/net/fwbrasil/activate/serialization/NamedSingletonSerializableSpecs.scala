package net.fwbrasil.activate.serialization

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.radon.dsl.actor._
import net.fwbrasil.activate.util.RichList._

@RunWith(classOf[JUnitRunner])
class NamedSingletonSerializableSpecs extends Specification {
 
	class NamedSingletonSerializableTest(val pName: String) extends NamedSingletonSerializable {
		def name = pName
	}

	"NamedSingletonSerializable" should {
		"be a singleton after serialization" in {
			"one thread" in {
				val singleton = new NamedSingletonSerializableTest("1")
				val serialized = javaSerializator.toSerialized(singleton)
				val restored = javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized)
				restored must beEqualTo(singleton)
			}

			"with many threads" in {
				"deserialization" in {
					new ActorDsl with ManyActors with OneActorPerThread {
						val singleton = new NamedSingletonSerializableTest("2")
						val serialized = javaSerializator.toSerialized(singleton)
						val restoredList =
							inParallelActors {
								javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized)
							}
						restoredList.toSet.size must beEqualTo(1)
						restoredList.toSet.head must beEqualTo(singleton)
					} must not beNull
				}

				"serialization" in {
					new ActorDsl with ManyActors with OneActorPerThread {
						val singleton = new NamedSingletonSerializableTest("3")
						val serializedList = inParallelActors {
							javaSerializator.toSerialized(singleton)
						}
						for (serialized <- serializedList)
							javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized) must beEqualTo(singleton)
					} must not beNull
				}

				"serialization and deserialization" in {
					new ActorDsl with ManyActors with OneActorPerThread {
						val singleton = new NamedSingletonSerializableTest("4")
						val serializedList = inParallelActors {
							javaSerializator.toSerialized(singleton)
						}
						val restoredList =
							inParallelActors {
								javaSerializator.fromSerialized[NamedSingletonSerializableTest](serializedList.randomElementOption.get)
							}
						restoredList.toSet.size must beEqualTo(1)
						restoredList.toSet.head must beEqualTo(singleton)
					} must not beNull
				}

				"creation, serialization and deserialization" in {
					new ActorDsl with ManyActors with OneActorPerThread {
						val singletonList = inParallelActors {
							new NamedSingletonSerializableTest(Thread.currentThread.toString)
						}
						val serializedList = inParallelActors {
							for(singleton <- singletonList)
								yield javaSerializator.toSerialized(singleton)
						}.flatten
						val restoredList = inParallelActors {
							for(serialized <- serializedList)
								yield javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized)
						}.flatten
						singletonList.toSet must beEqualTo(restoredList.toSet)
					} must not beNull
				}
			} 
		}
	}

}