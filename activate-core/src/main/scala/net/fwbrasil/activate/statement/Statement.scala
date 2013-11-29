package net.fwbrasil.activate.statement

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import From.runAndClearFrom
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.statement.query.Query
import java.lang.reflect.Field
import scala.collection.mutable.Stack
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import net.fwbrasil.activate.util.ConcurrentCache

trait StatementContext extends StatementValueContext with OperatorContext {

    private[activate] val liveCache: LiveCache

    // Use Any instead of Function1,2,3... There isn't a generic Function trait
    type Function = Any
    private val cache = new ConcurrentHashMap[(Class[_], Seq[Manifest[_]]), (List[Field], ConcurrentCache[(Function, Statement)])]()

    private[activate] def executeStatementWithParseCache[S <: Statement, R](f: Function, produce: () => S, execute: (S) => R, manifests: Manifest[_]*): R = {
        val (fields, stack) = {
            val key = (f.getClass.asInstanceOf[Class[Any]], manifests)
            var tuple = cache.get(key)
            if (tuple == null) {
                val fields = f.getClass.getDeclaredFields.toList.filter(field => !Modifier.isStatic(field.getModifiers))
                fields.foreach(_.setAccessible(true))
                tuple = (fields, new ConcurrentCache[(Function, Statement)]("StatementContext.cache", 20))
                cache.put(key, tuple)
            }
            tuple
        }
        val fromCacheOption = {
            val tuple = stack.poll
            if (tuple == null)
                None
            else {
                val (function, statement) = tuple
                Some(function, statement.asInstanceOf[S])
            }
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

    protected def mockEntity[E <: BaseEntity: Manifest]: E =
        mockEntity[E]()

    import language.existentials

    protected def mockEntity[E <: BaseEntity: Manifest](otherEntitySources: T forSome { type T <: BaseEntity }*): E = {
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