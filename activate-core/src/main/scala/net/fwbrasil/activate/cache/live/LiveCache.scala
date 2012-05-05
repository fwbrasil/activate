package net.fwbrasil.activate.cache.live

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.query.IsLessThan
import net.fwbrasil.activate.query.QueryEntityValue
import net.fwbrasil.activate.query.QueryValue
import net.fwbrasil.activate.query.IsGreaterOrEqualTo
import net.fwbrasil.activate.query.QueryEntitySourceValue
import net.fwbrasil.activate.query.EntitySource
import net.fwbrasil.activate.query.QueryEntitySourcePropertyValue
import net.fwbrasil.activate.query.IsEqualTo
import net.fwbrasil.activate.query.QuerySelectValue
import net.fwbrasil.activate.query.BooleanOperatorCriteria
import net.fwbrasil.activate.query.SimpleValue
import net.fwbrasil.activate.query.CompositeOperatorCriteria
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.IdVar
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.activate.query.Criteria
import net.fwbrasil.activate.query.SimpleQueryBooleanValue
import net.fwbrasil.radon.util.ReferenceWeakKeyMap
import net.fwbrasil.activate.query.QueryBooleanValue
import net.fwbrasil.activate.query.QueryEntityInstanceValue
import net.fwbrasil.activate.query.IsLessOrEqualTo
import net.fwbrasil.activate.query.Matcher
import net.fwbrasil.activate.util.CollectionUtil
import net.fwbrasil.activate.util.WildcardRegexUtil.wildcardToRegex
import net.fwbrasil.activate.query.IsGreaterThan
import net.fwbrasil.activate.query.From
import net.fwbrasil.activate.query.SimpleOperatorCriteria
import net.fwbrasil.activate.query.And
import net.fwbrasil.activate.query.Query
import net.fwbrasil.activate.util.Logging
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.query.Or
import net.fwbrasil.radon.util.ReferenceWeakValueMap
import java.util.Arrays.{ equals => arrayEquals }
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.util.ManifestUtil.manifestToClass
import scala.collection.mutable.{ HashMap => MutableHashMap }
import net.fwbrasil.activate.query.IsNull
import net.fwbrasil.activate.query.IsNotNull

class LiveCache(val context: ActivateContext) extends Logging {

	info("Initializing live cache for context " + context.contextName)

	import context._

	val cache =
		new MutableHashMap[Class[_ <: Entity], ReferenceWeakValueMap[String, _ <: Entity] with Lockable] with Lockable

	def reinitialize =
		logInfo("live cache reinitialize") {
			cache.doWithWriteLock {
				cache.clear
			}
		}

	def byId[E <: Entity: Manifest](id: String): Option[E] =
		entityInstacesMap[E].get(id)

	def contains[E <: Entity](entity: E) =
		entityInstacesMap(entity.niceClass).contains(entity.id)

	def delete(entity: Entity): Unit =
		delete(entity.id)

	def delete(entityId: String) = {
		val map = entityInstacesMap(EntityHelper.getEntityClassFromId(entityId))
		map.doWithWriteLock {
			map -= entityId
		}
	}

	def toCache[E <: Entity](entity: E): E =
		toCache(entity.niceClass, () => entity)

	def toCache[E <: Entity](entityClass: Class[E], fEntity: () => E): E = {
		val map = entityInstacesMap(entityClass)
		map.doWithWriteLock {
			val entity = fEntity()
			map += (entity.id -> entity)
			entity
		}
	}

	def isQueriable(entity: Entity) =
		entity.isInitialized && !entity.isDeletedSnapshot

	def fromCache[E <: Entity](entityClass: Class[E]) = {
		val entities = entityInstacesMap(entityClass).values.filter(isQueriable(_)).toList
		if (!storage.isMemoryStorage)
			for (entity <- entities)
				if (!entity.isPersisted)
					entity.initializeGraph
		entities
	}

	def entityInstacesMap[E <: Entity: Manifest]: ReferenceWeakValueMap[String, E] with Lockable =
		entityInstacesMap(manifestToClass(manifest[E]))

	def entityInstacesMap[E <: Entity](entityClass: Class[E]): ReferenceWeakValueMap[String, E] with Lockable = {
		val mapOption =
			cache.doWithReadLock {
				cache.get(entityClass)
			}
		if (mapOption != None)
			mapOption.get
		else {
			cache.doWithWriteLock {
				val entitiesMap = new ReferenceWeakValueMap[String, E] with Lockable
				cache += (entityClass -> entitiesMap)
				entitiesMap
			}
		}
	}.asInstanceOf[ReferenceWeakValueMap[String, E] with Lockable]

	def executeQuery[S](query: Query[S], iniatializing: Boolean): Set[S] = {
		var result =
			if (query.orderByClause.isDefined)
				query.orderByClause.get.emptyOrderedSet[S]
			else
				Set[S]()
		val entities = entitySourceInstancesCombined(query.from)
		val fromCache = executeQueryWithEntitySources(query, entities)
		object invalid
		val fromStorage = (for (line <- storage.fromStorage(query))
			yield toTuple[S](for (column <- line)
			yield column match {
			case value: EntityInstanceReferenceValue[_] =>
				if (value.value == None)
					null
				else if (iniatializing)
					materializeEntity(value.value.get)
				else
					materializeEntityIfNotDeleted(value.value.get).getOrElse(invalid)
			case other: EntityValue[_] =>
				other.value.getOrElse(null)
		}))
		result ++= fromStorage.filter({
			(row) =>
				row match {
					case row: Product =>
						row.productIterator.find(_ == invalid).isEmpty
					case other =>
						other != invalid
				}
		})
		result ++= fromCache
		result
	}

	def materializeEntity(entityId: String): Entity = {
		val entityClass = EntityHelper.getEntityClassFromId(entityId)
		entityInstacesMap(entityClass).getOrElse(entityId, {
			toCache(entityClass, () => createLazyEntity(entityClass, entityId))
		})
	}

	def materializeEntityIfNotDeleted(entityId: String): Option[Entity] = {
		val ret = materializeEntity(entityId)
		if (ret.isDeletedSnapshot)
			None
		else
			Some(ret)
	}

	def createLazyEntity[E <: Entity](entityClass: Class[E], entityId: String) = {
		val entity = newInstance[E](entityClass)
		val entityMetadata = EntityHelper.getEntityMetadata(entityClass)
		transactional(transient) {
			for (propertyMetadata <- entityMetadata.propertiesMetadata) {
				val typ = propertyMetadata.propertyType
				val field = propertyMetadata.varField
				val ref = new Var(typ, field.getName, entity)
				field.set(entity, ref)
			}
			val idField = entityMetadata.idField
			val ref = new IdVar(entity)
			idField.set(entity, ref)
			ref := entityId
			entity.setPersisted
			entity.setNotInitialized
			entity.invariants
			entity
		}
	}

	def initialize(entity: Entity) = {
		val vars = entity.vars.toList
		val varNames = vars.map(_.name)
		if (varNames != List("id")) {
			val list = query({ (e: Entity) =>
				where(toQueryValueEntity(e) :== entity.id) selectList ((for (name <- varNames) yield toQueryValueRef(e.varNamed(name).get)).toList)
			})(manifestClass(entity.niceClass)).execute(true)
			val row = list.headOption
			if (row.isDefined) {
				val tuple = row.get
				for (i <- 0 to vars.size - 1) {
					val ref = vars(i)
					val value = tuple.productElement(i)
					ref.setRefContent(Option(value))
				}
			} else entity.delete
		}
	}

	def executeQueryWithEntitySources[S](query: Query[S], entitySourcesInstancesCombined: List[List[Entity]]): List[S] = {
		val result = ListBuffer[S]()
		for (entitySourcesInstances <- entitySourcesInstancesCombined)
			result ++= executeQueryWithEntitySourcesMap(query, entitySourceInstancesMap(query.from, entitySourcesInstances))
		result.toList
	}

	def entitySourceInstancesMap(from: From, entitySourcesInstances: List[Entity]) = {
		var result = Map[EntitySource, Entity]()
		var i = 0
		for (entitySourceInstance <- entitySourcesInstances) {
			result += (from.entitySources(i) -> entitySourceInstance)
			i += 1
		}
		result
	}

	def executeQueryWithEntitySourcesMap[S](query: Query[S], entitySourceInstancesMap: Map[EntitySource, Entity]): List[S] = {
		val satisfyWhere = executeCriteria(query.where.value)(entitySourceInstancesMap)
		if (satisfyWhere) {
			List(executeSelect[S](query.select.values: _*)(entitySourceInstancesMap))
		} else
			List[S]()
	}

	def executeSelect[S](values: QuerySelectValue[_]*)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): S = {
		val list = ListBuffer[Any]()
		for (value <- values)
			list += executeQuerySelectValue(value)
		CollectionUtil.toTuple(list)
	}

	def executeCriteria(criteria: Criteria)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		criteria match {
			case criteria: BooleanOperatorCriteria =>
				executeBooleanOperatorCriteria(criteria)
			case criteria: SimpleOperatorCriteria =>
				executeSimpleOperatorCriteria(criteria)
			case criteria: CompositeOperatorCriteria =>
				executeCompositeOperatorCriteria(criteria)
		}

	def executeCompositeOperatorCriteria(criteria: CompositeOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		criteria.operator match {
			case operator: IsEqualTo =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				equals(a, b)
			case operator: IsGreaterThan =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a, b) > 0
			case operator: IsLessThan =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a, b) < 0
			case operator: IsGreaterOrEqualTo =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a, b) >= 0
			case operator: IsLessOrEqualTo =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB)
				(a != null && b != null) &&
					compare(a, b) <= 0
			case operator: Matcher =>
				val a = executeQueryValue(criteria.valueA)
				val b = executeQueryValue(criteria.valueB).asInstanceOf[String]
				(a != null && b != null) &&
					a.toString.matches(b)
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

	def executeSimpleOperatorCriteria(criteria: SimpleOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		criteria.operator match {
			case operator: IsNull =>
				executeQueryValue(criteria.valueA) == null
			case operator: IsNotNull =>
				executeQueryValue(criteria.valueA) != null
		}

	def executeBooleanOperatorCriteria(criteria: BooleanOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		criteria.operator match {
			case operator: And =>
				executeQueryBooleanValue(criteria.valueA) && executeQueryBooleanValue(criteria.valueB)
			case operator: Or =>
				executeQueryBooleanValue(criteria.valueA) || executeQueryBooleanValue(criteria.valueB)
		}

	def executeQueryBooleanValue(value: QueryBooleanValue)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		value match {
			case value: SimpleQueryBooleanValue =>
				value.value
			case value: Criteria =>
				executeCriteria(value)
		}

	def executeQueryValue(value: QueryValue)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any =
		value match {
			case value: Criteria =>
				executeCriteria(value)
			case value: QueryBooleanValue =>
				executeQueryBooleanValue(value)
			case value: QuerySelectValue[_] =>
				executeQuerySelectValue(value)

		}

	def executeQuerySelectValue(value: QuerySelectValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any =
		value match {
			case value: QueryEntityValue[_] =>
				executeQueryEntityValue(value)
			case value: SimpleValue[_] =>
				value.anyValue
		}

	def executeQueryEntityValue(value: QueryEntityValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any =
		value match {
			case value: QueryEntityInstanceValue[_] =>
				value.entity
			case value: QueryEntitySourceValue[_] =>
				executeQueryEntitySourceValue(value)
		}

	def executeQueryEntitySourceValue(value: QueryEntitySourceValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any = {
		val entity = entitySourceInstancesMap.get(value.entitySource).get
		value match {
			case value: QueryEntitySourcePropertyValue[_] =>
				entityPropertyPathRef(entity, value.propertyPathVars.toList)
			case value: QueryEntitySourceValue[_] =>
				entity
		}
	}

	def entityPropertyPathRef(entity: Entity, propertyPathVars: List[Var[_]]): Any =
		propertyPathVars match {
			case Nil =>
				null
			case propertyVar :: Nil =>
				entityProperty(entity, propertyVar.name)
			case propertyVar :: propertyPath => {
				val property = entityProperty[Entity](entity, propertyVar.name)
				if (property != null)
					entityPropertyPathRef(
						property,
						propertyPath)
				else
					null
			}
		}

	def entityProperty[T](entity: Entity, propertyName: String) =
		(entity.varNamed(propertyName) match {
			case None =>
				null
			case someRef: Some[Var[_]] =>
				!someRef.get
		}).asInstanceOf[T]

	def entitySourceInstancesCombined(from: From) =
		CollectionUtil.combine(entitySourceInstances(from.entitySources: _*))

	def entitySourceInstances(entitySources: EntitySource*) =
		for (entitySource <- entitySources)
			yield fromCache(entitySource.entityClass)

}
