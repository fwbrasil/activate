package net.fwbrasil.activate.statement

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import From.runAndClearFrom
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.statement.query.Query
import java.lang.reflect.Field
import scala.collection.mutable.Stack
import java.lang.reflect.Modifier

trait StatementContext extends StatementValueContext with OperatorContext {

	private[activate] val liveCache: LiveCache

	// Use Any instead of Function1,2,3... There isn't a generic Function trait
	type Function = Any
	val cache = MutableMap[(Class[_], Seq[Manifest[_]]), (List[Field], Stack[(Function, Statement)])]()

	protected def executeStatementWithCache[S <: Statement, R](f: Function, produce: () => S, execute: (S) => R, manifests: Manifest[_]*): R = {
		val (fields, stack) =
			cache.synchronized {
				cache.getOrElseUpdate((f.getClass.asInstanceOf[Class[_]], manifests), {
					val fields = f.getClass.getDeclaredFields.toList.filter(field => !Modifier.isStatic(field.getModifiers))
					fields.foreach(_.setAccessible(true))
					(fields, new Stack[(Function, Statement)]())
				})
			}
		val fromCacheOption =
			stack.synchronized {
				if (stack.isEmpty)
					None
				else {
					val (function, statement) = stack.pop
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
			stack.synchronized {
				stack.push((function, statement))
			}
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
		Where(value)

}

abstract class Statement(val from: From, val where: Where) {
	override def toString = from + " => where" + where
}