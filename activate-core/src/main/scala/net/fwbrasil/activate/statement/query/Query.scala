package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.statement.StatementContext
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.From.runAndClearFrom
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.StatementSelectValue

trait QueryContext extends StatementContext with OrderedQueryContext {

	private[activate] def queryInternal[E1 <: Entity: Manifest](f: (E1) => Query[Product]) =
		runAndClearFrom {
			f(mockEntity[E1])
		}

	def query[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1])
		}

	def executeQuery[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): List[S] =
		query(f).execute

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]): Query[S] =
		runAndClearFrom {
			val e1 = mockEntity[E1]
			val e2 = mockEntity[E2](e1)
			f(e1, e2)
		}

	def executeQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]): List[S] =
		query(f).execute

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3])
		}

	def executeQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]): List[S] =
		query(f).execute

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3],
				mockEntity[E4])
		}

	def executeQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]): List[S] =
		query(f).execute

	def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): Query[S] =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3],
				mockEntity[E4],
				mockEntity[E5])
		}

	def executeQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): List[S] =
		query(f).execute

	def allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
		query { (entity: E) =>
			where({
				var criteria = criterias(0)(entity)
				for (i <- 1 until criterias.size)
					criteria = criteria :&& criterias(i)(entity)
				criteria
			}) select (entity)
		}.execute

	def all[E <: Entity: Manifest] =
		allWhere[E](_ isNotNull)

	def byId[T <: Entity: Manifest](id: String): Option[T] = {
		val fromLiveCache = liveCache.byId[T](id)
		if (fromLiveCache.isDefined)
			if (fromLiveCache.get.isDeletedSnapshot)
				None
			else {
				fromLiveCache.get.initializeGraph
				fromLiveCache
			}
		else allWhere[T](_ :== id).headOption
	}

	private[activate] def executeQuery[S](query: Query[S], initializing: Boolean): List[S]

}

case class Query[S](from: From, where: Where, select: Select) extends Statement(from, where) {
	private[activate] def execute(iniatializing: Boolean): List[S] = {
		val context =
			(for (src <- from.entitySources)
				yield ActivateContext.contextFor(src.entityClass)).toSet.onlyOne
		context.executeQuery(this, iniatializing)
	}
	def execute: List[S] = execute(false)

	private[activate] def orderByClause: Option[OrderBy] = None

	override def toString = from + " => where" + where + " select " + select + ""
}

case class Select(values: StatementSelectValue[_]*) {
	override def toString = "(" + values.mkString(", ") + ")"
}

