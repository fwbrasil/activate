package net.fwbrasil.activate.statement.query

import java.util.IdentityHashMap
import scala.collection.mutable.ListBuffer
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
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.ManifestUtil._

object QueryNormalizer extends StatementNormalizer[Query[_]] {

    def normalizeStatement(query: Query[_]): List[Query[_]] = {
        val normalizedPropertyPath = normalizePropertyPath(List(query))
		val normalizedEagerEntities = normalizeEagerEntities(normalizedPropertyPath)
        val normalizedFrom = normalizeFrom(normalizedEagerEntities)
        val normalizedSelectWithOrderBy = normalizeSelectWithOrderBy(normalizedFrom)
        normalizedSelectWithOrderBy
    }

    def normalizeEagerEntities(queryList: List[Query[_]]): List[Query[_]] =
        queryList.map(normalizeEagerEntities(_))

    def normalizeEagerEntities(query: Query[_]): Query[_] = {
        val selectValues =
            (query.select.values.map {
                _ match {
                    case value: StatementEntitySourceValue[_] if(value.eager)=>
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

    def normalizePropertyPath[S](queryList: List[Query[S]]): List[Query[S]] =
        (for (query <- queryList)
            yield normalizePropertyPath(query)).flatten

    def normalizePropertyPath[S](query: Query[S]): List[Query[S]] = {
        var count = 0
        def nextNumber = {
            count += 1
            count
        }
        val nestedProperties = findObject[StatementEntitySourcePropertyValue](query) {
            _ match {
                case obj: StatementEntitySourcePropertyValue =>
                    obj.propertyPathVars.size > 1
                case other =>
                    false
            }
        }
        if (nestedProperties.nonEmpty) {
            var entitySourceSet = query.from.entitySources.toSet
            val criteriaList = ListBuffer[Criteria]()
            val normalizeMap = new IdentityHashMap[Any, Any]()
            for (nested <- nestedProperties) {
                val (entitySources, criterias, propValue) = normalizePropertyPath(nested, nextNumber)
                entitySourceSet ++= entitySources
                criteriaList ++= criterias
                normalizeMap.put(nested, propValue)
            }
            for (entitySource <- entitySourceSet)
                normalizeMap.put(entitySource, entitySource)
            val criteriaOption =
                query.where.valueOption.map {
                    value =>
                        var criteria = deepCopyMapping(value, normalizeMap)
                        for (i <- 0 until criteriaList.size)
                            criteria = And(criteria) :&& criteriaList(i)
                        Some(criteria)
                }.getOrElse {
                    criteriaList.toList match {
                        case Nil =>
                            None
                        case head :: Nil =>
                            Some(head)
                        case head :: tail =>
                            val criteria = tail.foldLeft(head)((base, criteria) => And(base) :&& criteria)
                            Some(criteria)
                    }
                }

            normalizeMap.put(query.where.valueOption, criteriaOption)
            normalizeMap.put(query.from, From(entitySourceSet.toSeq: _*))
            val result = deepCopyMapping(query, normalizeMap)
            List(result)
        } else
            List(query)
    }

    def normalizePropertyPath(nested: StatementEntitySourcePropertyValue, nextNumber: => Int) = {
        val entitySources = ListBuffer[EntitySource](nested.entitySource)
        val criterias = ListBuffer[Criteria]()
        for (i <- 0 until nested.propertyPathVars.size) {
            val prop = nested.propertyPathVars(i)
            val entitySource =
                if (i != 0) {
                    EntitySource(prop.outerEntityClass, "t" + nextNumber)
                } else
                    nested.entitySource
            if (i != 0) {
                criterias += (IsEqualTo(new StatementEntitySourcePropertyValue(entitySources.last, nested.propertyPathVars(i - 1))) :== new StatementEntitySourceValue(entitySource))
                entitySources += entitySource
            }
        }
        val propValue =
            new StatementEntitySourcePropertyValue(entitySources.last, nested.propertyPathVars.last)
        (entitySources, criterias, propValue)
    }

    def normalizeSelectWithOrderBy(queryList: List[Query[_]]): List[Query[_]] =
        for (query <- queryList)
            yield normalizeSelectWithOrderBy(query)

    def normalizeSelectWithOrderBy[S](query: Query[S]): Query[_] = {
        val orderByOption = query.orderByClause
        if (orderByOption.isDefined) {
            val select = query.select
            val orderByValues = orderByOption.get.criterias.map(_.value).filter(!select.values.contains(_))
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
                    case value: StatementEntitySourceValue[_] if(value.eager) =>
                        val values = value.explodedSelectValues.map(v => (v.lastVar.name, iterator.next)).toMap
                        ctx.liveCache.initializeEntityIfNecessary[Entity](values)(manifestClass(value.entityClass).asInstanceOf[Manifest[Entity]])
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

}