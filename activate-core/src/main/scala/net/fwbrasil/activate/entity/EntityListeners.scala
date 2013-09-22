package net.fwbrasil.activate.entity

import language.implicitConversions
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.radon.ref.Ref
import scala.collection.mutable.ListBuffer

trait EntityListeners {
    this: Entity =>
        
    @transient
    private val listeners = ListBuffer[RefListener[_]]()

    private implicit def funcToVarName(func: this.type => _) = {
        func(StatementMocks.mockEntity(this.getClass.asInstanceOf[Class[this.type]]))
        StatementMocks.lastFakeVarCalled.get.name
    }
    
    trait On {
        def update(f: => Unit): RefListener[_] = ???
        def properties: List[String]
    }

    case class On1[T1](prop1: String) extends On {
        def update(f: (T1) => Unit): RefListener[_] = ???
        def properties = List(prop1)
    }

    def on[T1](f1: this.type => T1) = {
        On1(f1)
    }

    protected def onUpdateOf(fVars: ((this.type) => Any)*)(f: => Unit) = {
        val vars =
            for (fVar <- fVars) yield {
                val mock = StatementMocks.mockEntity[this.type](this.getClass.asInstanceOf[Class[this.type]])
                fVar(mock)
                varNamed(StatementMocks.lastFakeVarCalled.get.name)
            }
        val listener = new RefListener[Any] {
            override def notifyPut(ref: Ref[Any], obj: Option[Any]) =
                f
        }
        vars.foreach(_.addWeakListener(listener))
        listener
    }

}