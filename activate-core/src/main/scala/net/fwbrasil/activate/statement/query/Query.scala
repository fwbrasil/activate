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
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.util.CollectionUtil

trait QueryContext extends StatementContext with OrderedQueryContext {
    this: ActivateContext =>

    private[activate] def executeQuery[S](query: Query[S]): List[S] = {
        val results =
            (for (normalized <- QueryNormalizer.normalize[Query[S]](query)) yield {
                liveCache.executeQuery(normalized)
            }).flatten
        val orderedResuts =
            query.orderByClause
                .map(order => results.sorted(order.ordering))
                .getOrElse(results)
        val tuples =
            QueryNormalizer
                .denormalizeSelectWithOrderBy(query, orderedResuts)
                .map(CollectionUtil.toTuple[S])
        query match {
            case query: LimitedOrderedQuery[_] =>
                tuples.take(query.limit)
            case other =>
                tuples
        }
    }

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

    @deprecated("Use select[Entity] where(_.column :== value)", since = "1.1")
    def allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
        _allWhere[E](criterias: _*)

    private def _allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
        allWhereQuery[E](criterias: _*).execute

    import language.postfixOps

    def all[E <: Entity: Manifest] =
        _allWhere[E](_ isNotNull)

    class SelectEntity[E <: Entity: Manifest] {
        def where(criterias: ((E) => Criteria)*) =
            _allWhere[E](criterias: _*)
    }

    def select[E <: Entity: Manifest] = new SelectEntity[E]

    def byId[T <: Entity](id: => String): Option[T] =
        EntityHelper.getEntityClassFromIdOption(id).flatMap {
            entityClass =>
                implicit val manifestT = manifestClass[T](entityClass)
                val fromLiveCache = liveCache.byId[T](id)
                if (fromLiveCache.isDefined)
                    fromLiveCache.filterNot(_.isDeletedSnapshot)
                else _allWhere[T](_ :== id).headOption
        }

}

class Query[S](override val from: From, override val where: Where, val select: Select) extends Statement(from, where) with Product {
    private[activate] def execute: List[S] = {
        val context =
            (for (src <- from.entitySources)
                yield ActivateContext.contextFor(src.entityClass)).toSet.onlyOne("All query entities sources must be from the same context.")
        context.executeQuery(this)
    }

    private[activate] def orderByClause: Option[OrderBy] = None

    def productElement(n: Int): Any =
        n match {
            case 0 => from
            case 1 => where
            case 2 => select
        }
    def productArity: Int = 3
    def canEqual(that: Any): Boolean =
        that.getClass == classOf[Query[S]]
    override def equals(that: Any): Boolean =
        canEqual(that) &&
            (that match {
                case v: Query[S] =>
                    v.from == from &&
                        v.where == where &&
                        v.select == select
                case other =>
                    false
            })

    override def toString = from + " => where" + where + " select " + select + ""
}

case class Select(values: StatementSelectValue[_]*) {
    override def toString = "(" + values.mkString(", ") + ")"
}