package net.fwbrasil.activate.entity

import language.implicitConversions
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.radon.ref.Ref
import scala.collection.mutable.ListBuffer

trait EntityListeners {
    this: Entity =>

    @transient
    private var listeners = List[RefListener[_]]()

    protected class On(functions: (EntityListeners.this.type => Any)*) {
        val vars = functions.map(StatementMocks.funcToVarName(_)).map(varNamed)
        def change(f: => Unit): RefListener[_] = {
            val listener = new RefListener[Any] {
                override def notifyPut(ref: Ref[Any], obj: Option[Any]) =
                    f
            }
            vars.foreach(_.addWeakListener(listener))
            listeners ++= List(listener)
            listener
        }
    }

    protected def on(functions: (EntityListeners.this.type => Any)*) = 
        new On(functions: _*)

}