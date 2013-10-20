package net.fwbrasil.activate.statement.query

import scala.concurrent.Future
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.From.runAndClearFrom
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.StatementContext
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.util.CollectionUtil
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.util.ManifestUtil.erasureOf
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import scala.concurrent.duration.Duration
import com.google.common.collect.MapMaker
import java.util.concurrent.ConcurrentHashMap

trait QueryContext extends StatementContext
        with OrderedQueryContext
        with CachedQueryContext
        with EagerQueryContext {
    this: ActivateContext =>

    def executeQuery[S](query: Query[S], onlyInMemory: Boolean = false): List[S] = {
        transactionManager.getRequiredActiveTransaction.startIfNotStarted
        val normalizedQueries = QueryNormalizer.normalize[Query[S]](query)
        val results =
            (for (normalized <- normalizedQueries) yield {
                liveCache.executeQuery(normalized, onlyInMemory)
            }).flatten
        treatResults(query, results)
    }

    private[activate] def queryInternal[E1 <: Entity: Manifest](f: (E1) => Query[Product]) =
        runAndClearFrom {
            f(mockEntity[E1])
        }

    def produceQuery[S, E1 <: Entity: Manifest, Q <: Query[S]](f: (E1) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1])
        }

    def paginatedQuery[S, E1 <: Entity: Manifest](f: (E1) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: Entity: Manifest](f: (E1) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, OrderedQuery[S]](f))

    def query[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1])

    def dynamicQuery[S, E1 <: Entity: Manifest](f: (E1) => Query[S]): List[S] =
        produceQuery[S, E1, Query[S]](f).execute

    def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, Q <: Query[S]](f: (E1, E2) => Q): Q =
        runAndClearFrom {
            val e1 = mockEntity[E1]
            val e2 = mockEntity[E2](e1)
            f(e1, e2)
        }

    def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, OrderedQuery[S]](f))

    def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2])

    def dynamicQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]): List[S] =
        produceQuery[S, E1, E2, Query[S]](f).execute

    def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, Q <: Query[S]](f: (E1, E2, E3) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3])
        }

    def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, OrderedQuery[S]](f))

    def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3])

    def dynamicQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, Query[S]](f).execute

    def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4])
        }

    def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, OrderedQuery[S]](f))

    def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4])

    def dynamicQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, Query[S]](f).execute

    def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4, E5) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4],
                mockEntity[E5])
        }

    def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, E5, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, E5, OrderedQuery[S]](f))

    def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, E5, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4],
            manifest[E5])

    def dynamicQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, E5, Query[S]](f).execute

    def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4, E5, E6) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4],
                mockEntity[E5],
                mockEntity[E6])
        }

    def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, E5, E6, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, E5, E6, OrderedQuery[S]](f))

    def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, E5, E6, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4],
            manifest[E5],
            manifest[E6])

    def dynamicQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, E5, E6, Query[S]](f).execute

    def produceQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest, E7 <: Entity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4, E5, E6, E7) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4],
                mockEntity[E5],
                mockEntity[E6],
                mockEntity[E7])
        }

    def paginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest, E7 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, E5, E6, E7, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest, E7 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, E5, E6, E7, OrderedQuery[S]](f))

    def query[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest, E7 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, E5, E6, E7, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4],
            manifest[E5],
            manifest[E6],
            manifest[E7])

    def dynamicQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest, E7 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, E5, E6, E7, Query[S]](f).execute

    private def allWhereQuery[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
        produceQuery[E, E, Query[E]] { (entity: E) =>
            val w =
                if (criterias.isEmpty)
                    where()
                else
                    where({
                        var criteria = criterias(0)(entity)
                        for (i <- 1 until criterias.size)
                            criteria = criteria :&& criterias(i)(entity)
                        criteria
                    })
            w.select(entity)
        }

    @deprecated("Use select[Entity] where(_.column :== value)", since = "1.1")
    def allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
        _allWhere[E](criterias: _*)

    private def _allWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*) =
        allWhereQuery[E](criterias: _*).execute

    import language.postfixOps

    def all[E <: Entity: Manifest] =
        query {
            (e: E) => where() select (e)
        }

    class SelectEntity[E <: Entity: Manifest] {
        def where(criterias: ((E) => Criteria)*) =
            _allWhere[E](criterias: _*)
    }

    def select[E <: Entity: Manifest] = new SelectEntity[E]

    def byId[T <: Entity](id: => String): Option[T] =
        EntityHelper.getEntityClassFromIdOption(id).map {
            entityClass => liveCache.materializeEntity(id, entityClass).asInstanceOf[T]
        }

    //ASYNC

    private def _asyncAllWhere[E <: Entity: Manifest](criterias: ((E) => Criteria)*)(implicit texctx: TransactionalExecutionContext) =
        allWhereQuery[E](criterias: _*).executeAsync

    def asyncAll[E <: Entity: Manifest](implicit texctx: TransactionalExecutionContext) =
        asyncQuery {
            (e: E) => where() select (e)
        }

    class AsyncSelectEntity[E <: Entity: Manifest] {
        def where(criterias: ((E) => Criteria)*)(implicit texctx: TransactionalExecutionContext) =
            _asyncAllWhere[E](criterias: _*)
    }

    def asyncSelect[E <: Entity: Manifest] = new AsyncSelectEntity[E]

    def asyncById[T <: Entity](id: => String)(implicit texctx: TransactionalExecutionContext): Future[Option[T]] = {
        EntityHelper.getEntityClassFromIdOption(id).map {
            entityClass =>
                implicit val manifestT = manifestClass[T](entityClass)
                val fromLiveCache = liveCache.byId[T](id)
                if (fromLiveCache.isDefined)
                    Future(fromLiveCache)(texctx)
                else _asyncAllWhere[T](_ :== id).map(_.headOption)(texctx)
        }.getOrElse(Future.successful(None))
    }

    def executeQueryAsync[S](query: Query[S], texctx: TransactionalExecutionContext): Future[List[S]] = {
        val normalizedQueries =
            QueryNormalizer
                .normalize[Query[S]](query)
        val future =
            normalizedQueries.foldLeft(Future(List[List[Any]]()))(
                (future, query) => future.flatMap(list => liveCache.executeQueryAsync(query)(texctx).map(list ++ _)))
        future.map(treatResults(query, _))
    }

    def asyncQuery[S, E1 <: Entity: Manifest](f: (E1) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        produceQuery[S, E1, Query[S]](f).executeAsync

    def asyncQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        produceQuery[S, E1, E2, Query[S]](f).executeAsync

    def asyncQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        produceQuery[S, E1, E2, E3, Query[S]](f).executeAsync

    def asyncQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        produceQuery[S, E1, E2, E3, E4, Query[S]](f).executeAsync

    def asyncQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        produceQuery[S, E1, E2, E3, E4, E5, Query[S]](f).executeAsync

    def asyncQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        produceQuery[S, E1, E2, E3, E4, E5, E6, Query[S]](f).executeAsync

    def asyncQuery[S, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest, E5 <: Entity: Manifest, E6 <: Entity: Manifest, E7 <: Entity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        produceQuery[S, E1, E2, E3, E4, E5, E6, E7, Query[S]](f).executeAsync

    private def treatResults[S](query: Query[S], results: List[List[Any]]): List[S] = {
        val orderedResuts =
            query.orderByClause
                .map(order => results.sorted(order.ordering))
                .getOrElse(results)
        val tuples =
            QueryNormalizer
                .denormalizeSelectResults(query, orderedResuts)
                .map(CollectionUtil.toTuple[S])
        query match {
            case query: LimitedOrderedQuery[_] =>
                tuples.take(query.limit)
            case other =>
                tuples
        }
    }

}