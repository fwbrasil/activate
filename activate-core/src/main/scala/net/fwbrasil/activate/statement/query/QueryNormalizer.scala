package net.fwbrasil.activate.statement.query

import java.util.IdentityHashMap
import scala.collection.mutable.{ ListBuffer, Map => MutableMap }
import scala.language.existentials
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.EntitySource
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementNormalizer
import net.fwbrasil.activate.util.CollectionUtil.combine
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.util.Reflection.deepCopyMapping
import net.fwbrasil.activate.util.Reflection.findObject
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.util.ManifestUtil._

object QueryNormalizer extends StatementNormalizer[Query[_]] {

    def normalizeStatement(query: Query[_]): List[Query[_]] = {
        val normalizedEagerEntities = normalizeEagerEntities(List(query))
        val normalizedPropertyPath = normalizePropertyPath(normalizedEagerEntities)
        val normalizedFrom = normalizeFrom(normalizedPropertyPath)
        val normalizedSelectWithOrderBy = normalizeSelectWithOrderBy(normalizedFrom)
        val normalizedOffset = normalizeOffset(query, normalizedSelectWithOrderBy)
        normalizedOffset
    }

    def normalizeOffset(originalQuery: Query[_], queryList: List[Query[_]]): List[Query[_]] =
        originalQuery match {
            case query: LimitedOrderedQuery[_] if (query.offsetOption.isDefined && queryList.size > 1) =>
                queryList.collect {
                    case query: LimitedOrderedQuery[_] =>
                        query.limit(query.limit + query.offsetOption.get).offset(0)
                }
            case other =>
                queryList
        }

    def normalizeEagerEntities(queryList: List[Query[_]]): List[Query[_]] =
        queryList.map(normalizeEagerEntities(_))

    def normalizeEagerEntities(query: Query[_]): Query[_] = {
        val selectValues =
            (query.select.values.map {
                _ match {
                    case value: StatementEntitySourceValue[_] if (value.eager) =>
                        value.explodedSelectValues
                    case other =>
                        List(other)
                }
            }).flatten
        if (query.select.values.size != selectValues.size) {
            val newSelect = Select(selectValues: _*)
            val normalizeMap = new IdentityHashMap[Any, Any]()
            normalizeMap.put(query.select, newSelect)
            deepCopyMapping(query, normalizeMap)
        } else
            query
    }

    def normalizePropertyPath[S](queryList: List[Query[_]]): List[Query[_]] =
        (for (query <- queryList)
            yield normalizePropertyPath(query)).toList

    def normalizePropertyPath(query: Query[_]): Query[_] = {
        val nestedProperties = findNestedProperties(query)
        if (nestedProperties.nonEmpty) {
            val pathNormalizationCache = MutableMap[(EntitySource, Var[Any]), (Criteria, EntitySource)]()
            val normalizationMap = new IdentityHashMap[Any, Any]()
            for (nested <- nestedProperties) {
                val path = nested.propertyPathVars.reverse.tail.reverse
                val entitySource = entitySourceForNormalizedPath(nested.entitySource, path.asInstanceOf[List[Var[Any]]])(pathNormalizationCache)
                val property = new StatementEntitySourcePropertyValue(entitySource, List(nested.propertyPathVars.last))
                normalizationMap.put(nested, property)
            }
            val entitySources = query.from.entitySources ++ pathNormalizationCache.values.map(_._2)
            normalizationMap.put(query.from, From(entitySources: _*))
            val criterias = pathNormalizationCache.values.map(_._1).toList
            val criteriaOption = normalizedWhereCriteria(query, normalizationMap, criterias)
            normalizationMap.put(query.where.valueOption, criteriaOption)
            deepCopyMapping(query, normalizationMap)
        } else
            query
    }

    def entitySourceForNormalizedPath(
        base: EntitySource,
        path: List[Var[Any]])(implicit pathNormalizationCache: MutableMap[(EntitySource, Var[Any]), (Criteria, EntitySource)]): EntitySource =

        path match {
            case Nil =>
                throw new IllegalStateException("Path should not be Nil.")
            case ref :: Nil =>
                entitySourceFor(base, ref)
            case ref :: tail =>
                entitySourceForNormalizedPath(entitySourceFor(base, ref), tail)
        }

    def entitySourceFor(base: EntitySource, ref: Var[Any])(implicit pathNormalizationCache: MutableMap[(EntitySource, Var[Any]), (Criteria, EntitySource)]) = {
        pathNormalizationCache.getOrElseUpdate((base, ref), {
            val entitySource = EntitySource(ref.valueClass.asInstanceOf[Class[BaseEntity]], "t" + (pathNormalizationCache.size + 1))
            val criteria = IsEqualTo(new StatementEntitySourcePropertyValue(base, List(ref))) :== new StatementEntitySourceValue(entitySource)
            (criteria, entitySource)
        })._2
    }

    def normalizeSelectWithOrderBy(queryList: List[Query[_]]): List[Query[_]] =
        for (query <- queryList)
            yield normalizeSelectWithOrderBy(query)

    def normalizeSelectWithOrderBy[S](query: Query[S]): Query[_] = {
        val orderByOption = query.orderByClause
        if (orderByOption.isDefined) {
            val select = query.select
            val orderByValues = orderByOption.get.criterias.map(_.value)
            val newSelect = Select(select.values ++ orderByValues: _*)
            val map = new IdentityHashMap[Any, Any]()
            map.put(select, newSelect)
            deepCopyMapping(query, map)
        } else query
    }

    def denormalizeSelectResults[S](originalQuery: Query[S], results: List[List[Any]])(implicit ctx: ActivateContext): List[List[Any]] = {
        val denormalizedEagerSelectEntities = denormalizeEagerSelectEntities(originalQuery, results)
        val denormalizedSelectWithOrderBy = denormalizeSelectWithOrderBy(originalQuery, denormalizedEagerSelectEntities)
        denormalizedSelectWithOrderBy
    }

    def denormalizeEagerSelectEntities[S](originalQuery: Query[S], results: List[List[Any]])(implicit ctx: ActivateContext): List[List[Any]] =
        for (columns <- results) yield {
            val iterator = columns.iterator
            originalQuery.select.values.toList.map {
                _ match {
                    case value: StatementEntitySourceValue[_] if (value.eager) =>
                        val values = value.explodedSelectValues.map(v => (v.lastVar.name, iterator.next)).toMap
                        ctx.liveCache.initializeEntityIfNecessary[BaseEntity](values)(manifestClass(value.entityClass).asInstanceOf[Manifest[BaseEntity]])
                    case other =>
                        iterator.next
                }
            }
        }

    def denormalizeSelectWithOrderBy[S](originalQuery: Query[S], results: List[List[Any]]): List[List[Any]] =
        if (originalQuery.orderByClause.isDefined)
            results.map(_.take(originalQuery.select.values.size))
        else
            results

    private def findNestedProperties(query: Query[_]) =
        findObject[StatementEntitySourcePropertyValue](query) {
            _ match {
                case obj: StatementEntitySourcePropertyValue =>
                    obj.propertyPathVars.size > 1
                case other =>
                    false
            }
        }

    private def normalizedWhereCriteria(query: Query[_], normalizationMap: IdentityHashMap[Any, Any], criterias: List[Criteria]) =
        query.where.valueOption.map {
            value =>
                var criteria = deepCopyMapping(value, normalizationMap)
                for (i <- 0 until criterias.size)
                    criteria = And(criteria) :&& criterias(i)
                Some(criteria)
        }.getOrElse {
            criterias match {
                case Nil =>
                    None
                case head :: Nil =>
                    Some(head)
                case head :: tail =>
                    val criteria = tail.foldLeft(head)((base, criteria) => And(base) :&& criteria)
                    Some(criteria)
            }
        }

}