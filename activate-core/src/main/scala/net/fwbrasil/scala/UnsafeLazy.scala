package net.fwbrasil.scala

import language.implicitConversions

object UnsafeLazy {

    case class UnsafeLazyItem[T](f: () => T) {
        private var item: T = _
        def initialized = item != null
        def get = {
            if (item == null)
                item = f() 
            item
        }
    }
    implicit def unsafeLazyToValue[T](item: UnsafeLazyItem[T]) = item.get
    def unsafeLazy[T](f: => T) = UnsafeLazyItem(() => f)
}