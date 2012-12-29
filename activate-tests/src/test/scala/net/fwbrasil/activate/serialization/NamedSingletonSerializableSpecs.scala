package net.fwbrasil.activate.serialization

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.radon.dsl.actor._
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.ThreadUtil._

@RunWith(classOf[JUnitRunner])
class NamedSingletonSerializableSpecs extends Specification {

	object lock

	class NamedSingletonSerializableTest(val pName: String) extends NamedSingletonSerializable {
		def name = pName
	}

	"NamedSingletonSerializable" should {
		"be a singleton after serialization" in {
			"one thread" in lock.synchronized {
				val singleton = new NamedSingletonSerializableTest("1")
				val serialized = javaSerializator.toSerialized(singleton)
				val restored = javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized)
				restored must beEqualTo(singleton)
			}

			"with many threads" in {
				"deserialization" in lock.synchronized {
					val singleton = new NamedSingletonSerializableTest("2")
					val serialized = javaSerializator.toSerialized(singleton)
					val restoredList =
						runWithThreads() {
							javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized)
						}
					restoredList.toSet.size must beEqualTo(1)
					restoredList.toSet.head must beEqualTo(singleton)
				}

				"serialization" in lock.synchronized {
					val singleton = new NamedSingletonSerializableTest("3")
					val serializedList =
						runWithThreads() {
							javaSerializator.toSerialized(singleton)
						}
					for (serialized <- serializedList)
						javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized) must beEqualTo(singleton)
				}

				"serialization and deserialization" in lock.synchronized {
					val singleton = new NamedSingletonSerializableTest("4")
					val serializedList = runWithThreads() {
						javaSerializator.toSerialized(singleton)
					}
					val restoredList =
						runWithThreads() {
							javaSerializator.fromSerialized[NamedSingletonSerializableTest](serializedList.randomElementOption.get)
						}
					restoredList.toSet.size must beEqualTo(1)
					restoredList.toSet.head must beEqualTo(singleton)
				}

				"creation, serialization and deserialization" in lock.synchronized {
					val singletonList = runWithThreads() {
						new NamedSingletonSerializableTest(Thread.currentThread.toString)
					}
					val serializedList = runWithThreads() {
						for (singleton <- singletonList)
							yield javaSerializator.toSerialized(singleton)
					}.flatten
					val restoredList = runWithThreads() {
						for (serialized <- serializedList)
							yield javaSerializator.fromSerialized[NamedSingletonSerializableTest](serialized)
					}.flatten
					singletonList.toSet must beEqualTo(restoredList.toSet)
				}
			}
		}
	}

}