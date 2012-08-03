package net.fwbrasil.activate.statement.query

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
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.EntitySource
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementNormalizer

object QueryNormalizer extends StatementNormalizer[Query[_]] {

	def normalizeStatement(query: Query[_]): List[Query[_]] = {
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
		val nestedProperties = findObject[StatementEntitySourcePropertyValue[_]](query) {
			(obj: Any) =>
				obj match {
					case obj: StatementEntitySourcePropertyValue[_] =>
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

	def normalizePropertyPath(nested: StatementEntitySourcePropertyValue[_], nextNumber: => Int) = {
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
				criterias += (IsEqualTo(StatementEntitySourcePropertyValue(entitySources.last, nested.propertyPathVars(i - 1))) :== StatementEntitySourceValue(entitySource))
				entitySources += entitySource
			}
		}
		val propValue =
			StatementEntitySourcePropertyValue(entitySources.last, nested.propertyPathVars.last)
		(entitySources, criterias, propValue)
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

	def denormalizeSelectWithOrderBy[S](originalQuery: Query[S], result: List[S]): List[S] = {
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