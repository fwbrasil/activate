package net.fwbrasil.activate.util

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class GraphUtilSpecs extends Specification {

	import GraphUtil._

	"DependencyTree" should {

		"resolve" in {
			"one root" in {
				testResolve(
					List(
						("a", "b"),
						("b", "c")),
					"a", "b", "c", "d") must beEqualTo(List("a", "b", "c", "d"))
			}
			"many roots" in {
				testResolve(
					List(
						("a", "b"),
						("b", "c"),
						("d", "e"),
						("f", "g"),
						("g", "h")),
					"a", "b", "c", "d", "e", "f", "g", "h") must beEqualTo(List("a", "b", "c", "d", "e", "f", "g", "h"))
			}
			"only one root" in {
				testResolve(
					List(),
					"a") must beEqualTo(List("a"))
			}
			"empty tree" in {
				testResolve(
					List()) must beEqualTo(List())
			}
			"a tree with null node" in {
				testResolve(
					List(
						("a", null)),
					"a", null) must beEqualTo(List("a", null))
			}
			"a tree with null nodes" in {
				testResolve(
					List(
						("a", null),
						(null, "b")),
					"a", "b", null) must beEqualTo(List("a", null, "b"))
			}
			"a tree with null root node" in {
				testResolve(
					List(
						(null, "a")),
					"a", null) must beEqualTo(List(null, "a"))
			}
		}

		"detect cyclic reference" in {
			"with root" in {
				testResolve(
					List(
						("a", "b"),
						("b", "c"),
						("c", "d"),
						("d", "b")),
					"a", "b", "c", "d") must throwA[IllegalStateException]
			}

			"with roots" in {
				testResolve(
					List(
						("a", "b"),
						("b", "c"),
						("c", "d"),
						("d", "b"),
						("e", "f")),
					"a", "b", "c", "d", "f") must throwA[IllegalStateException]
			}

			"without roots" in {
				testResolve(
					List(
						("a", "b"),
						("b", "a")),
					"a", "b") must throwA[IllegalStateException]
			}
			"the event case" in {
				testResolve(
					List(
						("room", "seat1"),
						("room", "seat2"),
						("seat1", "ticket1"),
						("seat2", "ticket2"),
						("event", "ticket1"),
						("event", "ticket2"),
						("room", "event")),
					"room", "event", "seat1", "ticket1", "seat2", "ticket2") must beEqualTo(List("room", "seat2", "seat1", "event", "ticket2", "ticket1"))
			}

			"with internal cycle" in {
				testResolve(
					List(
						("a", "b"),
						("c", "d"),
						("d", "c")),
					"a", "b", "c", "d") must throwA[IllegalStateException]
			}
		}

	}

	def testResolve[A: Manifest](edges: List[(A, A)], set: A*) = {
		val tree = new DependencyTree[A](set.toSet)
		for ((a, b) <- edges)
			tree.addDependency(a, b)
		tree.resolve
	}

}