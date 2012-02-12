package net.fwbrasil.activate.query

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import From.runAndClearFrom
import net.fwbrasil.activate.cache.live.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil.erasureOf

trait QueryContext extends QueryValueContext with OperatorContext with OrderedQueryContext {

	private[activate] val liveCache: LiveCache

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

	private[this] def mockEntity[E <: Entity: Manifest]: E =
		mockEntity[E]()

	private[this] def mockEntity[E <: Entity: Manifest](otherEntitySources: T forSome { type T <: Entity }*): E = {
		var mockEntity = QueryMocks.mockEntity(erasureOf[E])
		if (otherEntitySources.toSet.contains(mockEntity))
			mockEntity = QueryMocks.mockEntityWithoutCache(erasureOf[E])
		From.createAndRegisterEntitySource(erasureOf[E], mockEntity);
		mockEntity
	}

	def where(value: Criteria) =
		Where(value)

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
			if (fromLiveCache.get.isDeleted)
				None
			else
				fromLiveCache
		else allWhere[T](_ :== id).headOption
	}

	private[activate] def executeQuery[S](query: Query[S], initializing: Boolean): List[S]

}

case class Query[S](from: From, where: Where, select: Select) {
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

case class Select(values: QuerySelectValue[_]*) {
	override def toString = "(" + values.mkString(", ") + ")"
}

