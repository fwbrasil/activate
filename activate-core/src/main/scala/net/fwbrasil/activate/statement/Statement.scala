package net.fwbrasil.activate.statement

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import From.runAndClearFrom
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.statement.query.Query
import java.lang.reflect.Field
import scala.collection.mutable.Stack
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

trait StatementContext extends StatementValueContext with OperatorContext {

    private[activate] val liveCache: LiveCache

    // Use Any instead of Function1,2,3... There isn't a generic Function trait
    type Function = Any
    private val cache = new ConcurrentHashMap[(Class[_], Seq[Manifest[_]]), (List[Field], ConcurrentLinkedQueue[(Function, Statement)])]()

    private[activate] def executeStatementWithParseCache[S <: Statement, R](f: Function, produce: () => S, execute: (S) => R, manifests: Manifest[_]*): R = {
        val (fields, stack) = {
            var tuple = cache.get((f.getClass.asInstanceOf[Class[_]], manifests))
            if (tuple == null) {
                val fields = f.getClass.getDeclaredFields.toList.filter(field => !Modifier.isStatic(field.getModifiers))
                fields.foreach(_.setAccessible(true))
                tuple = (fields, new ConcurrentLinkedQueue[(Function, Statement)]())
            }
            tuple
        }
        val fromCacheOption =
            if (stack.isEmpty)
                None
            else {
                val (function, statement) = stack.poll
                Some(function, statement.asInstanceOf[S])
            }
        val (function, statement) =
            if (fromCacheOption.isDefined) {
                val (function, statement) = fromCacheOption.get
                for (field <- fields)
                    field.set(function, field.get(f))
                (function, statement)
            } else {
                val statement =
                    runAndClearFrom {
                        produce()
                    }
                (f, statement)
            }
        try
            execute(statement)
        finally
            stack.offer((function, statement))
    }

    protected def mockEntity[E <: Entity: Manifest]: E =
        mockEntity[E]()

    import language.existentials

    protected def mockEntity[E <: Entity: Manifest](otherEntitySources: T forSome { type T <: Entity }*): E = {
        var mockEntity = StatementMocks.mockEntity(erasureOf[E])
        if (otherEntitySources.toSet.contains(mockEntity))
            mockEntity = StatementMocks.mockEntityWithoutCache(erasureOf[E])
        From.createAndRegisterEntitySource(erasureOf[E], mockEntity);
        mockEntity
    }

    def where(value: Criteria) =
        Where(Some(value))

    def where() =
        Where(None)

}

abstract class Statement(val from: From, val where: Where) {
    override def toString = from + " => where" + where
}