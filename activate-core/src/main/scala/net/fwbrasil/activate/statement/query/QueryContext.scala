package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.util.Reflection.toRichClass
import scala.concurrent.Future
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.cache.LiveCache
import net.fwbrasil.activate.entity.BaseEntity
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
import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.util.ManifestUtil

trait QueryContext extends StatementContext
    with OrderedQueryContext
    with CachedQueryContext
    with EagerQueryContext {
    this: ActivateContext =>

    def executeQuery[S](query: Query[S], onlyInMemory: Boolean = false): List[S] = {
        startTransaction
        val normalizedQueries = QueryNormalizer.normalize[Query[S]](query)
        val results =
            (for (normalized <- normalizedQueries) yield {
                liveCache.executeQuery(normalized, onlyInMemory)
            }).flatten
        treatResults(query, normalizedQueries, results)
    }

    private[activate] def queryInternal[E1 <: BaseEntity: Manifest](f: (E1) => Query[Product]) =
        runAndClearFrom {
            f(mockEntity[E1])
        }

    def produceQuery[S, E1 <: BaseEntity: Manifest, Q <: Query[S]](f: (E1) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1])
        }

    def paginatedQuery[S, E1 <: BaseEntity: Manifest](f: (E1) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: BaseEntity: Manifest](f: (E1) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, OrderedQuery[S]](f))

    def query[S, E1 <: BaseEntity: Manifest](f: (E1) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1])

    def dynamicQuery[S, E1 <: BaseEntity: Manifest](f: (E1) => Query[S]): List[S] =
        produceQuery[S, E1, Query[S]](f).execute

    def produceQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, Q <: Query[S]](f: (E1, E2) => Q): Q =
        runAndClearFrom {
            val e1 = mockEntity[E1]
            val e2 = mockEntity[E2](e1)
            f(e1, e2)
        }

    def paginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest](f: (E1, E2) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest](f: (E1, E2) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, OrderedQuery[S]](f))

    def query[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest](f: (E1, E2) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2])

    def dynamicQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest](f: (E1, E2) => Query[S]): List[S] =
        produceQuery[S, E1, E2, Query[S]](f).execute

    def produceQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, Q <: Query[S]](f: (E1, E2, E3) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3])
        }

    def paginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest](f: (E1, E2, E3) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest](f: (E1, E2, E3) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, OrderedQuery[S]](f))

    def query[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest](f: (E1, E2, E3) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3])

    def dynamicQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest](f: (E1, E2, E3) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, Query[S]](f).execute

    def produceQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4])
        }

    def paginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest](f: (E1, E2, E3, E4) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest](f: (E1, E2, E3, E4) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, OrderedQuery[S]](f))

    def query[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest](f: (E1, E2, E3, E4) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4])

    def dynamicQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest](f: (E1, E2, E3, E4) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, Query[S]](f).execute

    def produceQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4, E5) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4],
                mockEntity[E5])
        }

    def paginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, E5, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, E5, OrderedQuery[S]](f))

    def query[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): List[S] =
        executeStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, E5, Query[S]](f),
            (query: Query[S]) => query.execute,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4],
            manifest[E5])

    def dynamicQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, E5, Query[S]](f).execute

    def produceQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4, E5, E6) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4],
                mockEntity[E5],
                mockEntity[E6])
        }

    def paginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, E5, E6, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, E5, E6, OrderedQuery[S]](f))

    def query[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6) => Query[S]): List[S] =
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

    def dynamicQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, E5, E6, Query[S]](f).execute

    def produceQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest, E7 <: BaseEntity: Manifest, Q <: Query[S]](f: (E1, E2, E3, E4, E5, E6, E7) => Q): Q =
        runAndClearFrom {
            f(mockEntity[E1],
                mockEntity[E2],
                mockEntity[E3],
                mockEntity[E4],
                mockEntity[E5],
                mockEntity[E6],
                mockEntity[E7])
        }

    def paginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest, E7 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => OrderedQuery[S]): Pagination[S] =
        new Pagination(produceQuery[S, E1, E2, E3, E4, E5, E6, E7, OrderedQuery[S]](f))

    def asyncPaginatedQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest, E7 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => OrderedQuery[S]): AsyncPagination[S] =
        new AsyncPagination(produceQuery[S, E1, E2, E3, E4, E5, E6, E7, OrderedQuery[S]](f))

    def query[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest, E7 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => Query[S]): List[S] =
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

    def dynamicQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest, E7 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => Query[S]): List[S] =
        produceQuery[S, E1, E2, E3, E4, E5, E6, E7, Query[S]](f).execute

    private def allWhereQuery[E <: BaseEntity: Manifest](criterias: ((E) => Criteria)*) =
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

    @deprecated("Use select[BaseEntity] where(_.column :== value)", since = "1.1")
    def allWhere[E <: BaseEntity: Manifest](criterias: ((E) => Criteria)*) =
        _allWhere[E](criterias: _*)

    private def _allWhere[E <: BaseEntity: Manifest](criterias: ((E) => Criteria)*) =
        allWhereQuery[E](criterias: _*).execute

    import language.postfixOps

    def all[E <: BaseEntity: Manifest] =
        query {
            (e: E) => where() select (e)
        }

    class SelectEntity[E <: BaseEntity: Manifest] {
        def where(criterias: ((E) => Criteria)*) =
            _allWhere[E](criterias: _*)
    }

    def select[E <: BaseEntity: Manifest] = new SelectEntity[E]

    def byId[T <: BaseEntity: Manifest](id: => T#ID, initialized: Boolean = true): Option[T] =
        byId(id, erasureOf[T], initialized)

    def byId[T <: BaseEntity](id: => T#ID, entityClass: Class[T], initialized: Boolean = true) = {
        startTransaction
        val manifest = ManifestUtil.manifestClass[BaseEntity](entityClass)
        val isPolymorfic =
            EntityHelper.concreteClasses(entityClass) != List(entityClass)
        if (isPolymorfic)
            dynamicQuery {
                (e: BaseEntity) => where(e.id :== id) select (e)
            }(manifest).headOption.asInstanceOf[Option[T]]
        else
            Some(liveCache.materializeEntity(id, entityClass.asInstanceOf[Class[BaseEntity]]).asInstanceOf[T])
                .filter(entity => !initialized || !entity.isDeleted)
    }

    //ASYNC

    private def _asyncAllWhere[E <: BaseEntity: Manifest](criterias: ((E) => Criteria)*)(implicit texctx: TransactionalExecutionContext) =
        allWhereQuery[E](criterias: _*).executeAsync

    def asyncAll[E <: BaseEntity: Manifest](implicit texctx: TransactionalExecutionContext) =
        asyncQuery {
            (e: E) => where() select (e)
        }

    class AsyncSelectEntity[E <: BaseEntity: Manifest] {
        def where(criterias: ((E) => Criteria)*)(implicit texctx: TransactionalExecutionContext) =
            _asyncAllWhere[E](criterias: _*)
    }

    def asyncSelect[E <: BaseEntity: Manifest] = new AsyncSelectEntity[E]

    def asyncById[T <: BaseEntity: Manifest](id: => T#ID, initialized: Boolean = true)(implicit texctx: TransactionalExecutionContext): Future[Option[T]] =
        asyncById[T](id, erasureOf[T], initialized)

    def asyncById[T <: BaseEntity](id: => T#ID, entityClass: Class[T], initialized: Boolean = true)(implicit texctx: TransactionalExecutionContext) = {
        import texctx.ctx._
        texctx.transactional(byId(id, entityClass, initialized = false)).map { entity =>
            if (initialized)
                asyncInitialize(entity)
            else
                Future.successful(Some(entity))
        }.getOrElse(Future.successful(None))
    }

    protected def asyncCacheHit = {}
    protected def asyncCacheMiss = {}

    private def asyncInitialize[T <: BaseEntity](entity: T)(implicit texctx: TransactionalExecutionContext): Future[Option[T]] = {
        import texctx.ctx._
        if (!entity.isInitialized) {
            asyncCacheMiss
            entity.setInitializing
            liveCache.asyncLoadFromDatabase(entity)
                .asInstanceOf[Future[Option[T]]]
                .map(_.map { entity =>
                    entity.setInitialized
                    entity
                })
        } else {
            asyncCacheHit
            Future(Some(entity).filter(!_.isDeleted))
        }
    }

    def executeQueryAsync[S](query: Query[S], texctx: TransactionalExecutionContext): Future[List[S]] = {
        val normalizedQueries =
            QueryNormalizer
                .normalize[Query[S]](query)
        val future =
            normalizedQueries.foldLeft(Future(List[List[Any]]()))(
                (future, query) => future.flatMap(list => liveCache.executeQueryAsync(query)(texctx).map(list ++ _)))
        future.map(treatResults(query, normalizedQueries, _))
    }

    def asyncQuery[S, E1 <: BaseEntity: Manifest](f: (E1) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        asyncExecuteStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, Query[S]](f),
            (query: Query[S]) => query.executeAsync,
            manifest[E1])(texctx.ctx.ectx)

    def asyncQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest](f: (E1, E2) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        asyncExecuteStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, Query[S]](f),
            (query: Query[S]) => query.executeAsync,
            manifest[E1],
            manifest[E2])(texctx.ctx.ectx)

    def asyncQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest](f: (E1, E2, E3) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        asyncExecuteStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, Query[S]](f),
            (query: Query[S]) => query.executeAsync,
            manifest[E1],
            manifest[E2],
            manifest[E3])(texctx.ctx.ectx)

    def asyncQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest](f: (E1, E2, E3, E4) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        asyncExecuteStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, Query[S]](f),
            (query: Query[S]) => query.executeAsync,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4])(texctx.ctx.ectx)

    def asyncQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        asyncExecuteStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, E5, Query[S]](f),
            (query: Query[S]) => query.executeAsync,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4],
            manifest[E5])(texctx.ctx.ectx)

    def asyncQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        asyncExecuteStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, E5, E6, Query[S]](f),
            (query: Query[S]) => query.executeAsync,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4],
            manifest[E5],
            manifest[E6])(texctx.ctx.ectx)

    def asyncQuery[S, E1 <: BaseEntity: Manifest, E2 <: BaseEntity: Manifest, E3 <: BaseEntity: Manifest, E4 <: BaseEntity: Manifest, E5 <: BaseEntity: Manifest, E6 <: BaseEntity: Manifest, E7 <: BaseEntity: Manifest](f: (E1, E2, E3, E4, E5, E6, E7) => Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[S]] =
        asyncExecuteStatementWithParseCache[Query[S], List[S]](
            f,
            () => produceQuery[S, E1, E2, E3, E4, E5, E6, E7, Query[S]](f),
            (query: Query[S]) => query.executeAsync,
            manifest[E1],
            manifest[E2],
            manifest[E3],
            manifest[E4],
            manifest[E5],
            manifest[E6],
            manifest[E7])(texctx.ctx.ectx)

    private def treatResults[S](query: Query[S], normalized: List[Query[S]], results: List[List[Any]]): List[S] = {
        val orderedResuts =
            query.orderByClause
                .map(order => results.sorted(order.ordering))
                .getOrElse(results)
        val tuples =
            QueryNormalizer
                .denormalizeSelectResults(query, orderedResuts)
                .map(CollectionUtil.toTuple[S])
        query match {
            case query: LimitedOrderedQuery[_] if (query.offsetOption.isDefined && normalized.size > 1) =>
                tuples.drop(query.offsetOption.get).take(query.limit)
            case query: LimitedOrderedQuery[_] =>
                tuples.take(query.limit)
            case other =>
                tuples
        }
    }

    private def startTransaction =
        transactionManager.getRequiredActiveTransaction.startIfNotStarted

}