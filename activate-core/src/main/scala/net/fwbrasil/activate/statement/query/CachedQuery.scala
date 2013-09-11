package net.fwbrasil.activate.statement.query

import scala.collection.JavaConversions._
import net.fwbrasil.activate.entity.Entity
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.Modifier
import net.fwbrasil.activate.ActivateContext
import java.util.concurrent.ConcurrentSkipListSet
import java.util.NoSuchElementException
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.util.ManifestUtil._
import java.util.concurrent.ConcurrentLinkedQueue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.entity.LazyList
import net.fwbrasil.radon.transaction.NestedTransaction
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.Reflection

class CachedQuery[E <: Entity: Manifest](clazz: Class[_])(implicit context: ActivateContext) {

    import context._

    type Function = Any

    val fields = clazz.getDeclaredFields.toList.filter(field => !Modifier.isStatic(field.getModifiers))

    fields.foreach(_.setAccessible(true))

    val parseCache = new ConcurrentLinkedQueue[(Function, Query[String])]
    val resultsCache = new ConcurrentHashMap[List[Any], Set[String]]

    def deleteEntities(entities: List[Entity]) =
        for (values <- resultsCache.keys()) {
            val newSet = resultsCache.get(values) -- entities.map(_.id).toSet
            resultsCache.put(values, newSet)
        }

    def updateEntities(transaction: Transaction, entities: List[Entity]) = {
        val nested = new NestedTransaction(transaction)
        transactional(nested) {
            for (values <- resultsCache.keys()) {
                val newSet = resultsCache.get(values) -- entities.map(_.id).toSet
                val query = queryFor(values, None)
                val entitySource = query.from.entitySources.head
                val entitiesToAdd =
                    entities.filter {
                        entity => context.liveCache.executeCriteria(query.where.valueOption)(Map(entitySource -> entity))
                    }
                resultsCache.put(values, newSet ++ entitiesToAdd.map(_.id))
            }
        }
        nested.rollback
    }

    def execute(f: (E) => Where) = {
        val (values, query) = valuesAndQuery(f)
        try {
            val results =
                context.executeQuery(query, onlyInMemory = true).toSet ++
                    fromResultsCache(query, values)
            new LazyList(results.toList)
        } finally
            parseCache.offer(f, query)
    }

    def valuesAndQuery(f: (E) => Where) = {
        val values = fields.map(_.get(f))
        (values, queryFor(values, Some(f)))
    }

    def queryFor(values: List[Any], fOption: Option[(E) => Where]) = {
        val tuple = parseCache.poll()
        if (tuple != null) {
            val (function, query) = tuple
            setValues(function, values)
            query
        } else {
            val function =
                fOption.getOrElse {
                    Reflection.newInstance(clazz)
                        .asInstanceOf[(E) => Where]
                }
            setValues(function, values)
            val query =
                context.produceQuery[String, E, Query[String]] {
                    (e: E) => function(e).select(e.id)
                }
            query
        }
    }

    private def setValues(function: Function, values: List[Any]) =
        for ((field, value) <- fields.zip(values)) yield {
            field.set(function, value)
        }

    private def fromResultsCache(query: Query[String], values: List[Any]) = {
        var results = resultsCache.get(values)
        if (results == null) {
            results = context.executeQuery(query, onlyInMemory = false).toSet
            resultsCache.put(values, results)
        }
        results.filter(id => byId[E](id).filter(!_.isDirty).isDefined)
    }

}

trait CachedQueryContext {
    this: ActivateContext =>

    private val cachedQueries = new ConcurrentHashMap[(Class[_], Class[_]), CachedQuery[_]]()

    private[activate] def clearCachedQueries =
        cachedQueries.clear

    private[activate] def updateCachedQueries(
        transaction: Transaction,
        inserts: List[Entity],
        updates: List[Entity],
        deletes: List[Entity]) =
        if (!cachedQueries.isEmpty) {
            for (((functionClass, entityClass), cachedQuery) <- cachedQueries) {

                val filteredDeletes =
                    deletes.filter(insert => entityClass.isAssignableFrom(insert.getClass))
                if (filteredDeletes.nonEmpty)
                    cachedQuery.deleteEntities(filteredDeletes)

                val filteredUpdates =
                    (updates ++ inserts).filter(insert => entityClass.isAssignableFrom(insert.getClass))
                if (filteredUpdates.nonEmpty)
                    cachedQuery.updateEntities(transaction, filteredUpdates)
            }
        }

    def cachedQuery[E <: Entity: Manifest](f: (E) => Where) = {
        val functionClass = f.getClass
        val entityClass = erasureOf[E]
        var cachedQuery =
            cachedQueries.get(functionClass, entityClass).asInstanceOf[CachedQuery[E]]
        if (cachedQuery == null) {
            cachedQuery = new CachedQuery[E](functionClass)
            cachedQueries.put((functionClass, entityClass), cachedQuery)
        }
        cachedQuery.execute(f)
    }

}