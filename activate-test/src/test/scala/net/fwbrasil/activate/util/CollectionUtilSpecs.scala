
package net.fwbrasil.activate.util

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class CollectionUtilSpecs extends Specification {

    import CollectionUtil.{ combine, toTuple => toTupleUtil }

    "CollectionUtil" should {

        "combine" in {
            List(
                List(List())
                    -> List(),
                List(List("a"))
                    -> List(List("a")),
                List(List("a"), List("b"))
                    -> List(List("a", "b")),
                List(List("a", "b"), List("c"))
                    -> List(List("a", "c"), List("b", "c")),
                List(List("a"), List("b", "c"))
                    -> List(List("a", "b"), List("a", "c")),
                List(List("a", "b", "c"), List())
                    -> List()).foreach {
                    case (list, combined) =>
                        combine(list) must beEqualTo(combined)
                } must not beNull
        }

        "tupelize" in {
            List(
                Seq("a")
                    -> "a",
                Seq("a", "b")
                    -> Tuple2("a", "b"),
                Seq("a", "b", "c")
                    -> Tuple3("a", "b", "c"),
                Seq("a", "b", "c", "d")
                    -> Tuple4("a", "b", "c", "d"),
                Seq("a", "b", "c", "d", "e")
                    -> Tuple5("a", "b", "c", "d", "e")).foreach {
                    case (list, tuple) =>
                        toTupleUtil[tuple.type](list) must beEqualTo(tuple)
                } must not beNull
        }
    }
}