package net.fwbrasil.activate.statement.query

import scala.language.implicitConversions

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementSelectValue

object EagerQueryContext {
    
     private val eagerFlag =
        new ThreadLocal[Boolean] {
            override def initialValue = false
        }
     
     def setEager = 
         eagerFlag.set(true)
         
     def isEager = 
         try eagerFlag.get
         finally eagerFlag.set(false)
}

trait EagerQueryContext {
    this: ActivateContext =>

    implicit class EagerEntity[E <: Entity: Manifest](entity: => E)(implicit tval: (=> E) => StatementSelectValue) {
        def eager = {
            tval(entity) match {
//                case value: StatementEntitySourcePropertyValue =>
//                    fail(value)
                case value: StatementEntitySourceValue[_] =>
                	EagerQueryContext.setEager
                case other =>
                    fail(other)
            }
            entity
        }
        def fail(value: StatementSelectValue) = 
            throw new UnsupportedOperationException("Triyng to define eager loading for a value that isn't an entity source: " + value)
    }

}