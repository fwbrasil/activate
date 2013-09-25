package net.fwbrasil.activate.serialization

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.ThreadUtil._

@RunWith(classOf[JUnitRunner])
class NamedSingletonSerializableSpecs extends Specification {

    args.execute(threadsNb = 1)
    
    object lock

    class NamedSingletonSerializableTest(val pName: String) extends NamedSingletonSerializable {
        def name = pName
    }

    "NamedSingletonSerializable" should {
        "be a singleton after serialization" in {
            "one thread" in lock.synchronized {
                val singleton = new NamedSingletonSerializableTest("1")
                val serialized = javaSerializer.toSerialized(singleton)
                val restored = javaSerializer.fromSerialized[NamedSingletonSerializableTest](serialized)
                restored must beEqualTo(singleton)
            }

            "with many threads" in {
                "deserialization" in lock.synchronized {
                    val singleton = new NamedSingletonSerializableTest("2")
                    val serialized = javaSerializer.toSerialized(singleton)
                    val restoredList =
                        runWithThreads() {
                            javaSerializer.fromSerialized[NamedSingletonSerializableTest](serialized)
                        }
                    restoredList.toSet.size must beEqualTo(1)
                    restoredList.toSet.head must beEqualTo(singleton)
                }

                "serialization" in lock.synchronized {
                    val singleton = new NamedSingletonSerializableTest("3")
                    val serializedList =
                        runWithThreads() {
                            javaSerializer.toSerialized(singleton)
                        }
                    for (serialized <- serializedList)
                        javaSerializer.fromSerialized[NamedSingletonSerializableTest](serialized) must beEqualTo(singleton)
                    ok
                }

                "serialization and deserialization" in lock.synchronized {
                    val singleton = new NamedSingletonSerializableTest("4")
                    val serializedList = runWithThreads() {
                        javaSerializer.toSerialized(singleton)
                    }
                    val restoredList =
                        runWithThreads() {
                            javaSerializer.fromSerialized[NamedSingletonSerializableTest](serializedList.randomElementOption.get)
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
                            yield javaSerializer.toSerialized(singleton)
                    }.flatten
                    val restoredList = runWithThreads() {
                        for (serialized <- serializedList)
                            yield javaSerializer.fromSerialized[NamedSingletonSerializableTest](serialized)
                    }.flatten
                    singletonList.toSet must beEqualTo(restoredList.toSet)
                }
            }
        }
    }

}