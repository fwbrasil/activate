package net.fwbrasil.activate.query

import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.CollectionUtil.combine
import net.fwbrasil.activate.util.Reflection._
import scala.collection.mutable.{ ListBuffer, Map => MutableMap }

object QueryNormalizer {

	val cache = new HashMap[Query[_], List[Query[_]]]() with SynchronizedMap[Query[_], List[Query[_]]]

	def normalize[S](query: Query[S]): List[Query[S]] =
		cache.getOrElseUpdate(query, normalizeQuery(query)).asInstanceOf[List[Query[S]]]

	def normalizeQuery[S](query: Query[S]): List[Query[S]] = {
		val normalizedFrom = normalizeFrom(List(query))
		val normalizedPropertyPath = normalizePropertyPath(normalizedFrom)
		normalizedPropertyPath
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
			val normalizeMap = MutableMap[Any, Any]()
			for (nested <- nestedProperties) {
				val (entitySources, criterias, propValue) = normalizePropertyPath(nested, nextNumber)
				entitySourceList ++= entitySources
				criteriaList ++= criterias
				normalizeMap += (nested -> propValue)
			}
			var criteria = query.where.value
			for (i <- 0 until criteriaList.size)
				criteria = And(criteria) :&& criteriaList(i)
			normalizeMap += (query.where.value -> criteria)
			normalizeMap += (query.from -> From(entitySourceList: _*))
			List(deepCopyMapping(query, normalizeMap.toMap))
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
				criterias += (IsEqualTo(QueryEntitySourcePropertyValue(entitySources.last, nested.propertyPathVars(i-1))) :== QueryEntitySourceValue(entitySource))
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
				yield EntityHelper.concreteClasses(entitySource.entityClass.asInstanceOf[Class[Entity]]).toSeq).toSeq
		val combined = combine(concreteClasses)
		val originalSources = query.from.entitySources
		val fromMaps =
			for (classes <- combined) yield (for (i <- 0 until classes.size)
				yield (originalSources(i) -> EntitySource(classes(i), originalSources(i).name))).toMap
		for (fromMap <- fromMaps) yield deepCopyMapping(query, fromMap)
	}

}