package net.fwbrasil.activate.query

import java.util.IdentityHashMap
import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.CollectionUtil.combine
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.util.Reflection.deepCopyMapping
import net.fwbrasil.activate.util.Reflection.findObject
import net.fwbrasil.activate.util.RichList.toRichList
import scala.collection.mutable.{ ListBuffer, Map => MutableMap }
import scala.collection.immutable.TreeSet

object QueryNormalizer {

	val cache = new HashMap[Query[_], List[Query[_]]]() with SynchronizedMap[Query[_], List[Query[_]]]

	def normalize[S](query: Query[S]): List[Query[S]] =
		cache.getOrElseUpdate(query, normalizeQuery(query)).asInstanceOf[List[Query[S]]]

	def normalizeQuery[S](query: Query[S]): List[Query[_]] = {
		val normalizedPropertyPath = normalizePropertyPath(List(query))
		val normalizedFrom = normalizeFrom(normalizedPropertyPath)
		val normalizedSelectWithOrderBy = normalizeSelectWithOrderBy(normalizedFrom)
		normalizedSelectWithOrderBy
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
		val nestedProperties = findObject[QueryEntitySourcePropertyValue[_]](query) {
			(obj: Any) =>
				obj match {
					case obj: QueryEntitySourcePropertyValue[_] =>
						obj.propertyPathVars.size > 1
					case other =>
						false
				}
		}
		if (nestedProperties.nonEmpty) {
			val entitySourceList = ListBuffer[EntitySource]()
			val criteriaList = ListBuffer[Criteria]()
			val normalizeMap = new IdentityHashMap[Any, Any]()
			for (nested <- nestedProperties) {
				val (entitySources, criterias, propValue) = normalizePropertyPath(nested, nextNumber)
				entitySourceList ++= entitySources
				criteriaList ++= criterias
				normalizeMap.put(nested, propValue)
			}
			for (entitySource <- entitySourceList)
				normalizeMap.put(entitySource, entitySource)
			var criteria = deepCopyMapping(query.where.value, normalizeMap)
			for (i <- 0 until criteriaList.size)
				criteria = And(criteria) :&& criteriaList(i)
			normalizeMap.put(query.where.value, criteria)
			normalizeMap.put(query.from, From(entitySourceList: _*))
			List(deepCopyMapping(query, normalizeMap))
		} else
			List(query)
	}

	def normalizePropertyPath(nested: QueryEntitySourcePropertyValue[_], nextNumber: => Int) = {
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
				criterias += (IsEqualTo(QueryEntitySourcePropertyValue(entitySources.last, nested.propertyPathVars(i - 1))) :== QueryEntitySourceValue(entitySource))
				entitySources += entitySource
			}
		}
		val propValue =
			QueryEntitySourcePropertyValue(entitySources.last, nested.propertyPathVars.last)
		(entitySources, criterias, propValue)
	}

	def normalizeFrom[S](queryList: List[Query[S]]): List[Query[S]] =
		(for (query <- queryList)
			yield normalizeFrom(query)).flatten

	def normalizeFrom[S](query: Query[S]): List[Query[S]] = {
		val concreteClasses =
			(for (entitySource <- query.from.entitySources)
				yield EntityHelper.concreteClasses(entitySource.entityClass).toSeq).toSeq
		val combined = combine(concreteClasses)
		val originalSources = query.from.entitySources
		val fromMaps =
			for (classes <- combined) yield {
				val fromMap = new IdentityHashMap[Any, Any]()
				for (i <- 0 until classes.size)
					fromMap.put(originalSources(i), EntitySource(classes(i), originalSources(i).name))
				fromMap
			}
		for (fromMap <- fromMaps) yield deepCopyMapping(query, fromMap)
	}

	def normalizeSelectWithOrderBy[S](queryList: List[Query[S]]): List[Query[_]] =
		for (query <- queryList)
			yield normalizeSelectWithOrderBy(query)

	def normalizeSelectWithOrderBy[S](query: Query[S]): Query[_] = {
		val orderByOption = query.orderByClause
		if (orderByOption.isDefined) {
			val orderByValues = orderByOption.get.criterias.map(_.value)
			val select = query.select
			val newSelect = Select(select.values ++ orderByValues: _*)
			val map = new IdentityHashMap[Any, Any]()
			map.put(select, newSelect)
			deepCopyMapping(query, map)
		} else query
	}

	def denormalizeSelectWithOrderBy[S](originalQuery: Query[S], result: Set[S]): List[S] = {
		val orderByOption = originalQuery.orderByClause
		val ret = if (orderByOption.isDefined) {
			val size = originalQuery.select.values.size
			var list = ListBuffer[S]()
			for (row <- result)
				list += toTuple[S](for (i <- 0 until size) yield row.asInstanceOf[Product].productElement(i))
			list.toList
		} else result.toList
		ret
	}

}