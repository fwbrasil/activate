package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.From.runAndClearFrom
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.StatementContext
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.statement.StatementMocks
import scala.collection.mutable.{ Map => MutableMap }
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import scala.collection.mutable.Stack
import net.fwbrasil.activate.storage.Storage

trait QueryContext extends StatementContext with OrderedQueryContext {

	val storage: Storage[_]

	private[activate] def queryInternal[E1 <: Entity: Manifest](f: (E1) => Query[Product]) =
		runAndClearFrom {
			f(mockEntity[E1])
		}

	def produceQuery[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1])
		}

	def paginatedQuery[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): Pagination[S] =
		new Pagination(produceQuery(f).execute)

	def query[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): List[S] =
		executeStatementWithCache[Query[S], List[S]](
			f,
			() => produceQuery(f),
			(query: Query[S]) => query.execute,
			manifest[E1])

	def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]): Query[S] =
		runAndClearFrom {
			val e1 = mockEntity[E1]
			val e2 = mockEntity[E2](e1)
			f(e1, e2)
		}

	def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]): Pagination[S] =
		new Pagination(produceQuery(f).execute)

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]): List[S] =
		executeStatementWithCache[Query[S], List[S]](
			f,
			() => produceQuery(f),
			(query: Query[S]) => query.execute,
			manifest[E1],
			manifest[E2])

	def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3])
		}

	def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]): Pagination[S] =
		new Pagination(produceQuery(f).execute)

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]): List[S] =
		executeStatementWithCache[Query[S], List[S]](
			f,
			() => produceQuery(f),
			(query: Query[S]) => query.execute,
			manifest[E1],
			manifest[E2],
			manifest[E3])

	def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3],
				mockEntity[E4])
		}

	def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]): Pagination[S] =
		new Pagination(produceQuery(f).execute)

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]): List[S] =
		executeStatementWithCache[Query[S], List[S]](
			f,
			() => produceQuery(f),
			(query: Query[S]) => query.execute,
			manifest[E1],
			manifest[E2],
			manifest[E3],
			manifest[E4])

	def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3],
				mockEntity[E4],
				mockEntity[E5])
		}

	def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): Pagination[S] =
		new Pagination(produceQuery(f).execute)

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): List[S] =
		executeStatementWithCache[Query[S], List[S]](
			f,
			() => produceQuery(f),
			(query: Query[S]) => query.execute,
			manifest[E1],
			manifest[E2],
			manifest[E3],
			manifest[E4],
			manifest[E5])

	private def allWhereQuery[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
		produceQuery { (entity: E) =>
			where({
				var criteria = criterias(0)(entity)
				for (i <- 1 until criterias.size)
					criteria = criteria :&& criterias(i)(entity)
				criteria
			}).select(entity)
		}

	def allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
		allWhereQuery[E](criterias: _*).execute

	def all[E <: Entity: Manifest] =
		allWhere[E](_ isNotNull)

	def byId[T <: Entity: Manifest](id: => String): Option[T] = {
		val fromLiveCache = liveCache.byId[T](id)
		if (fromLiveCache.isDefined)
			if (fromLiveCache.get.isDeletedSnapshot)
				None
			else {
				if (!storage.isMemoryStorage)
					fromLiveCache.get.initializeGraph
				fromLiveCache
			}
		else allWhere[T](_ :== id).headOption
	}

	private[activate] def executeQuery[S](query: Query[S], initializing: Boolean): List[S]

}

case class Query[S](override val from: From, override val where: Where, select: Select) extends Statement(from, where) {
	private[activate] def execute(iniatializing: Boolean): List[S] = {
		val context =
			(for (src <- from.entitySources)
				yield ActivateContext.contextFor(src.entityClass)).toSet.onlyOne("All query entities sources must be from the same context.")
		context.executeQuery(this, iniatializing)
	}
	def execute: List[S] = execute(false)

	private[activate] def orderByClause: Option[OrderBy] = None

	override def toString = from + " => where" + where + " select " + select + ""
}

case class Select(values: StatementSelectValue[_]*) {
	override def toString = "(" + values.mkString(", ") + ")"
}