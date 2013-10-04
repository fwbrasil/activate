package net.fwbrasil.activate.entity

import language.implicitConversions
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.radon.ref.Ref
import scala.collection.mutable.ListBuffer

trait EntityListeners {
    this: Entity =>

    @transient
    private var listeners: List[Any] = null

    protected def beforeConstruct = {}
    protected def afterConstruct = {}
    protected def beforeInitialize = {}
    protected def afterInitialize = {}
    protected def beforeDelete = {}
    protected def afterDelete = {}

    protected class On(val vars: List[Var[Any]]) {
        def change(f: => Unit): RefListener[_] = {
            val listener = new RefListener[Any] {
                override def notifyPut(ref: Ref[Any], obj: Option[Any]) =
                    if (EntityListeners.this.isInitialized)
                        f
            }
            vars.foreach(_.addWeakListener(listener))
            listener
        }
    }

    private[activate] def initializeListeners =
        listeners = entityMetadata.listenerMethods.map(_.invoke(this))

    protected def onAny =
        new On(vars)

    protected def on(functions: (EntityListeners.this.type => Any)*) =
        new On(functions.map(StatementMocks.funcToVarName(_)).map(varNamed).toList)

}