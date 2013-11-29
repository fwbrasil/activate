package net.fwbrasil.activate.entity

import language.implicitConversions
import net.fwbrasil.activate.ActivateContext
import scala.collection.SeqView
import scala.collection.TraversableView.NoBuilder
import scala.collection.generic.CanBuildFrom
import scala.collection.TraversableView

case class LazyList[E <: BaseEntity](val ids: List[E#ID])(implicit val m: Manifest[E]) {
    def toList()(implicit context: ActivateContext) =
        ids.map(context.byId[E](_)).flatten.toList
    def view()(implicit context: ActivateContext) =
        ids.view.map(context.byId[E](_)).flatten
    override def toString = "LazyList(" + ids.mkString(", ") + ")"
}

object LazyList {
    def apply[E <: BaseEntity](entities: E*)(implicit m: Manifest[E]) =
        new LazyList[E](entities.map(_.id).toList)
}

trait LazyListContext {

    implicit def listToLazyList[E <: BaseEntity: Manifest](list: List[E]) =
        new LazyList(list.map(_.id).toList)

    implicit def lazyListToList[E <: BaseEntity](lazyList: LazyList[E])(implicit m: Manifest[E], context: ActivateContext) =
        lazyList.toList
}