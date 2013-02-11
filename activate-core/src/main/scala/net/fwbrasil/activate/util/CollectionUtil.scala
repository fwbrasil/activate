package net.fwbrasil.activate.util

import net.fwbrasil.scala.Tuple23
import net.fwbrasil.scala.Tuple27
import net.fwbrasil.scala.Tuple24
import net.fwbrasil.scala.Tuple25
import net.fwbrasil.scala.Tuple26
import net.fwbrasil.scala.Tuple28
import net.fwbrasil.scala.Tuple29
import net.fwbrasil.scala.Tuple29
import net.fwbrasil.scala.Tuple30
import net.fwbrasil.scala.Tuple31
import net.fwbrasil.scala.Tuple31
import net.fwbrasil.scala.Tuple32

object CollectionUtil {

    def combine[T](lists: Seq[Seq[T]]) =
        (if (lists.nonEmpty)
            ((lists.map(_.map(Seq(_))))
            .reduceLeft((xs, ys) => for { x <- xs; y <- ys } yield x ++ y).toList)
        else List(List[T]()))
            .asInstanceOf[List[List[T]]]

    def toTuple[T](seq: Seq[_]) =
        (seq.size match {
            case 1 =>
                seq(0)
            case 2 =>
                Tuple2(seq(0), seq(1))
            case 3 =>
                Tuple3(seq(0), seq(1), seq(2))
            case 4 =>
                Tuple4(seq(0), seq(1), seq(2), seq(3))
            case 5 =>
                Tuple5(seq(0), seq(1), seq(2), seq(3), seq(4))
            case 6 =>
                Tuple6(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5))
            case 7 =>
                Tuple7(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6))
            case 8 =>
                Tuple8(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7))
            case 9 =>
                Tuple9(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8))
            case 10 =>
                Tuple10(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9))
            case 11 =>
                Tuple11(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10))
            case 12 =>
                Tuple12(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11))
            case 13 =>
                Tuple13(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12))
            case 14 =>
                Tuple14(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13))
            case 15 =>
                Tuple15(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14))
            case 16 =>
                Tuple16(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15))
            case 17 =>
                Tuple17(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16))
            case 18 =>
                Tuple18(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17))
            case 19 =>
                Tuple19(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18))
            case 20 =>
                Tuple20(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19))
            case 21 =>
                Tuple21(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20))
            case 22 =>
                Tuple22(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21))
            case 23 =>
                new Tuple23(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22))
            case 24 =>
                new Tuple24(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23))
            case 25 =>
                new Tuple25(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24))
            case 26 =>
                new Tuple26(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24), seq(25))
            case 27 =>
                new Tuple27(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24), seq(25), seq(26))
            case 28 =>
                new Tuple28(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24), seq(25), seq(26), seq(27))
            case 29 =>
                new Tuple29(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24), seq(25), seq(26), seq(27), seq(28))
            case 30 =>
                new Tuple30(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24), seq(25), seq(26), seq(27), seq(28), seq(29))
            case 31 =>
                new Tuple31(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24), seq(25), seq(26), seq(27), seq(28), seq(29), seq(30))
            case 32 =>
                new Tuple32(seq(0), seq(1), seq(2), seq(3), seq(4), seq(5), seq(6), seq(7), seq(8), seq(9), seq(10), seq(11), seq(12), seq(13), seq(14), seq(15), seq(16), seq(17), seq(18), seq(19), seq(20), seq(21), seq(22), seq(23), seq(24), seq(25), seq(26), seq(27), seq(28), seq(29), seq(30), seq(31))
            case other =>
                throw new IllegalStateException("Seq has too many itens to be a tuple!")
        }).asInstanceOf[T]

}