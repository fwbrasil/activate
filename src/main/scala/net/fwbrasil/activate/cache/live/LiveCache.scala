package net.fwbrasil.activate.cache.live

import java.util.Arrays.{ equals => arrayEquals }
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.entity.EntityValue.tvalFunction
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import java.util.Collections
import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query._
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.util.CollectionUtil
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.radon.util.ReferenceWeakValueMap
import net.fwbrasil.radon.util.ReferenceWeakKeyMap
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.ActivateContext

class LiveCache(val context: ActivateContext) {

	def storage = context.storage

	type E = Entity

	private[activate] val cache =
		new ReferenceWeakKeyMap[Class[E], ReferenceWeakValueMap[String, E] with Lockable] with Lockable

	def reinitialize =
		cache.doWithWriteLock {
			cache.clear
		}

	def contains(entity: E) =
		entityInstacesMap(entity.getClass.asInstanceOf[Class[E]]).contains(entity.id)

	def cachedInstance(entity: E) =
		entityInstacesMap(entity.getClass.asInstanceOf[Class[E]]).getOrElse(entity.id, {
			toCache(entity)
			entity.boundVarsToEntity
			entity
		})

	def delete(entity: => E) = {
		val map = entityInstacesMap(entity.getClass.asInstanceOf[Class[E]])
		map.doWithWriteLock {
			map -= entity.id
		}
		entity
	}

	def toCache(entity: => E) = {
		val map = entityInstacesMap(entity.getClass.asInstanceOf[Class[E]])
		map.doWithWriteLock {
			map += (entity.id -> entity)
		}
		entity
	}

	def fromCache(entityClass: Class[E]) =
		entityInstacesMap(entityClass).values.filter((entity: Entity) => !entity.isDeleted && entity.isInitialized).toList

	def entityInstacesMap(entityClass: Class[E]) = {
		val mapOption =
			cache.doWithReadLock {
				cache.get(entityClass)
			}
		if (mapOption != None)
			mapOption.get
		else {
			cache.doWithWriteLock {
				val entitiesMap = new ReferenceWeakValueMap[String, E] with Lockable
				cache += (entityClass.asInstanceOf[Class[E]] -> entitiesMap)
				entitiesMap
			}
		}
	}

	def executeQuery[S](query: Query[S]): List[S] = {
		val fromCache = executeQueryWithEntitySources(query, entitySourceInstancesCombined(query.from))
		val fromStorage = (for (line <- storage.fromStorage(query))
			yield toTuple[S](for (column <- line)
			yield column match {
			case value: EntityInstanceReferenceValue[_] =>
				Option(materializeEntity(value))
			case other: EntityValue[_] =>
				other.value
		}))
		(fromCache ::: fromStorage).toSet.toList
	}

	def materializeEntity(value: EntityInstanceReferenceValue[_]): E = {
		if (value.value == None)
			null
		else {
			val entityId = value.value.get
			val entityClass = value.entityClass.asInstanceOf[Class[E]]
			entityInstacesMap(entityClass).getOrElse(entityId, {
				toCache(createLazyEntity(entityClass, entityId))
			})
		}
	}

	def createLazyEntity[E](entityClass: Class[E], entityId: String) = {
		val entity = newInstance[Entity](entityClass)
		val (idField, fields) = net.fwbrasil.activate.entity.Entity.getEntityFields(entityClass.asInstanceOf[Class[Entity]])
		context.transactional(context.transient) {
			for ((field, typ) <- fields) {
				val ref = new Var[Any](null)(manifestClass(typ), tvalFunction(typ), context)
				field.set(entity, ref)
			}
		}
		idField.setAccessible(true)
		idField.set(entity, entityId)
		entity.boundVarsToEntity
		entity.setPersisted
		entity.setNotInitialized
		entity
	}

	def initialize(entity: Entity) = {
		import context._
		val list = query({ (e: Entity) =>
			where(toQueryValueEntity(e) :== entity.id) selectList ((for (ref <- e.vars) yield toQueryValue(ref)).toList)
		})(manifestClass(entity.getClass)).execute
		val tuple = list.first
		val vars = entity.vars.toList
		for (i <- 0 to vars.size - 1)
			vars(i).asInstanceOf[Var[Any]].setRefContent(tuple.productElement(i).asInstanceOf[Option[Any]])
	}

	def executeQueryWithEntitySources[S](query: Query[S], entitySourcesInstancesCombined: List[List[E]]): List[S] = {
		val result = ListBuffer[S]()
		for (entitySourcesInstances <- entitySourcesInstancesCombined)
			result ++= executeQueryWithEntitySourcesMap(query, entitySourceInstancesMap(query.from, entitySourcesInstances))
		result.toList
	}

	def entitySourceInstancesMap(from: From, entitySourcesInstances: List[E]) = {
		var result = Map[EntitySource, E]()
		var i = 0
		for (entitySourceInstance <- entitySourcesInstances) {
			result += (from.entitySources(i) -> entitySourceInstance)
			i += 1
		}
		result
	}

	def executeQueryWithEntitySourcesMap[S](query: Query[S], entitySourceInstancesMap: Map[EntitySource, E]): List[S] = {
		val satisfyWhere = executeCriteria(query.where.value)(entitySourceInstancesMap)
		if (satisfyWhere) {
			List(executeSelect[S](query.select.values: _*)(entitySourceInstancesMap))
		} else
			List[S]()
	}

	def executeSelect[S](values: QuerySelectValue[_]*)(implicit entitySourceInstancesMap: Map[EntitySource, E]): S = {
		val list = ListBuffer[Any]()
		for (value <- values)
			list += Option(executeQuerySelectValue(value))
		CollectionUtil.toTuple(list)
	}

	def executeCriteria(criteria: Criteria)(implicit entitySourceInstancesMap: Map[EntitySource, E]): Boolean =
		criteria match {
			case criteria: BooleanOperatorCriteria =>
				executeBooleanOperatorCriteria(criteria)
			case criteria: SimpleOperatorCriteria =>
				executeSimpleOperatorCriteria(criteria)
			case criteria: CompositeOperatorCriteria =>
				executeCompositeOperatorCriteria(criteria)
		}

	def executeCompositeOperatorCriteria(criteria: CompositeOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, E]): Boolean =
		criteria.operator match {
			case operator: IsEqualTo =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				equals(a, b)
			case operator: IsGreaterThan =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a,b) > 0
			case operator: IsLessThan =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a,b) < 0
			case operator: IsGreaterOrEqualTo =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a,b) >= 0
			case operator: IsLessOrEqualTo =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a,b) <= 0
		}
	

	def compare(a: Any, b: Any) =
		(a.asInstanceOf[Comparable[Any]].compareTo(b))
	
	def equals(a: Any, b: Any) =
		if (a == null)
			b == null
		else
			(a, b) match {
				case (a: Array[Byte], b: Array[Byte]) =>
					arrayEquals(a, b)
				case (a, b) =>
					valueForEquals(a).equals(valueForEquals(b))
			}

	def valueForEquals(a: Any) =
		a match {
			case entity: Entity =>
				entity.id
			case other =>
				other
		}

	def executeSimpleOperatorCriteria(criteria: SimpleOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, E]): Boolean =
		criteria.operator match {
			case operator: IsNone =>
				executeQueryValue(criteria.valueA) == null
			case operator: IsSome =>
				executeQueryValue(criteria.valueA) != null
		}

	def executeBooleanOperatorCriteria(criteria: BooleanOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, E]): Boolean =
		criteria.operator match {
			case operator: And =>
				executeQueryBooleanValue(criteria.valueA) && executeQueryBooleanValue(criteria.valueB)
			case operator: Or =>
				executeQueryBooleanValue(criteria.valueA) || executeQueryBooleanValue(criteria.valueB)
		}

	def executeQueryBooleanValue(value: QueryBooleanValue)(implicit entitySourceInstancesMap: Map[EntitySource, E]): Boolean =
		value match {
			case value: SimpleQueryBooleanValue =>
				value.value
			case value: Criteria =>
				executeCriteria(value)
		}

	def executeQueryValue(value: QueryValue)(implicit entitySourceInstancesMap: Map[EntitySource, E]): Any =
		value match {
			case value: Criteria =>
				executeCriteria(value)
			case value: QueryBooleanValue =>
				executeQueryBooleanValue(value)
			case value: QuerySelectValue[_] =>
				executeQuerySelectValue(value)

		}

	def executeQuerySelectValue(value: QuerySelectValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, E]): Any =
		value match {
			case value: QueryEntityValue[_] =>
				executeQueryEntityValue(value)
			case value: SimpleValue[_] =>
				value.anyValue
		}

	def executeQueryEntityValue(value: QueryEntityValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, E]): Any =
		value match {
			case value: QueryEntityInstanceValue[_] =>
				value.entity
			case value: QueryEntitySourceValue[_] =>
				executeQueryEntitySourceValue(value)
		}

	def executeQueryEntitySourceValue(value: QueryEntitySourceValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, E]): Any = {
		val entity = entitySourceInstancesMap.get(value.entitySource).get
		value match {
			case value: QueryEntitySourcePropertyValue[_] =>
				entityPropertyPathRef(entity, value.propertyPath.toList)
			case value: QueryEntitySourceValue[_] =>
				entity
		}
	}

	def entityPropertyPathRef(entity: E, propertyPath: List[String]): Any =
		propertyPath match {
			case Nil =>
				null
			case propertyName :: Nil =>
				entityProperty(entity, propertyName)
			case propertyName :: propertyPath => {
				val property = entityProperty(entity, propertyName)
				if (property != null)
					entityPropertyPathRef(
						property.asInstanceOf[E],
						propertyPath
					)
				else
					null
			}
		}

	def entityProperty(entity: E, propertyName: String) =
		entity.varNamed(propertyName) match {
			case None =>
				null
			case someRef: Some[Var[_]] =>
				!someRef.get
		}

	def entitySourceInstancesCombined(from: From) =
		CollectionUtil.combine(entitySourceInstances(from.entitySources: _*))

	def entitySourceInstances(entitySources: EntitySource*) =
		for (entitySource <- entitySources)
			yield fromCache(entitySource.entityClass.asInstanceOf[Class[E]])

}
