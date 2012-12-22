package net.fwbrasil.activate.cache.live

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.StatementEntityValue
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.EntitySource
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.IdVar
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.radon.util.ReferenceWeakKeyMap
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.util.CollectionUtil
import net.fwbrasil.activate.util.WildcardRegexUtil.wildcardToRegex
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.util.Logging
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.statement.Or
import java.util.Arrays.{ equals => arrayEquals }
import net.fwbrasil.activate.util.CollectionUtil.toTuple
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.util.ManifestUtil.manifestToClass
import net.fwbrasil.activate.util.RichList._
import scala.collection.mutable.{ HashMap => MutableHashMap }
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.radon.transaction.TransactionManager
import net.fwbrasil.activate.statement.mass.UpdateAssignment
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.radon.util.ReferenceSoftValueMap
import net.fwbrasil.activate.entity.EntityMetadata
import org.joda.time.base.AbstractInstant
import net.fwbrasil.activate.entity.ListEntityValue
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.ReferenceListEntityValue

class LiveCache(val context: ActivateContext) extends Logging {

	info("Initializing live cache for context " + context.contextName)

	import context._

	val cache =
		new MutableHashMap[Class[_ <: Entity], ReferenceSoftValueMap[String, _ <: Entity] with Lockable] with Lockable

	def reinitialize =
		logInfo("live cache reinitialize") {
			cache.doWithWriteLock {
				cache.clear
			}
		}

	def byId[E <: Entity: Manifest](id: String): Option[E] =
		entityInstacesMap[E].get(id)

	def contains[E <: Entity](entity: E) = {
		val map = entityInstacesMap(entity.niceClass)
		map.doWithReadLock(map.contains(entity.id))
	}

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

	def isQueriable(entity: Entity, isMassModification: Boolean) =
		entity.isInitialized && !entity.isDeletedSnapshot && (isMassModification || storage.isMemoryStorage || !entity.isPersisted || entity.isDirty)

	def fromCache[E <: Entity](entityClass: Class[E], isMassModification: Boolean) = {
		val map = entityInstacesMap(entityClass)
		map.doWithReadLock {
			val entities = map.values.filter(isQueriable(_, isMassModification)).toList
			if (!storage.isMemoryStorage)
				for (entity <- entities)
					if (!entity.isPersisted)
						entity.initializeGraph
			entities
		}
	}

	def entityInstacesMap[E <: Entity: Manifest]: ReferenceSoftValueMap[String, E] with Lockable =
		entityInstacesMap(manifestToClass(manifest[E]))

	def entityInstacesMap[E <: Entity](entityClass: Class[E]): ReferenceSoftValueMap[String, E] with Lockable = {
		val mapOption =
			cache.doWithReadLock {
				cache.get(entityClass)
			}
		if (mapOption != None)
			mapOption.get
		else {
			cache.doWithWriteLock {
				cache.get(entityClass).getOrElse {
					val entitiesMap = new ReferenceSoftValueMap[String, E] with Lockable
					cache += (entityClass -> entitiesMap)
					entitiesMap
				}
			}
		}
	}.asInstanceOf[ReferenceSoftValueMap[String, E] with Lockable]

	def executeMassModification(statement: MassModificationStatement) = {
		val entities = entitySourceInstancesCombined(true, statement.from)
		executeMassModificationWithEntitySources(statement, entities)
	}

	private def entitiesFromCache[S](query: Query[S]) = {
		val entities = entitySourceInstancesCombined(false, query.from)
		executeQueryWithEntitySources(query, entities)
	}

	object invalid

	private def filterInvalid[S](list: List[S]) =
		list.filter({
			(row) =>
				row match {
					case row: Product =>
						row.productIterator.find(_ == invalid).isEmpty
					case other =>
						other != invalid
				}
		})

	private def entitiesFromStorage[S](query: Query[S], initializing: Boolean) = {
		val fromStorageMaterialized =
			for (line <- storage.fromStorage(query))
				yield toTuple[S](for (column <- line)
				yield materialize(column, initializing))
		filterInvalid(fromStorageMaterialized)
	}

	def materialize(value: EntityValue[_], initializing: Boolean) =
		value match {
			case value: ReferenceListEntityValue[_] =>
				value.value.map(_.map(_.map(materializeEntity).orNull)).orNull
			case value: EntityInstanceReferenceValue[_] =>
				materializeReference(value, initializing)
			case other: EntityValue[_] =>
				other.value.getOrElse(null)
		}

	private def materializeReference(value: EntityInstanceReferenceValue[_], initializing: Boolean) = {
		if (value.value == None)
			null
		else if (initializing)
			materializeEntity(value.value.get)
		else
			materializeEntityIfNotDeleted(value.value.get).getOrElse(invalid)
	}

	def executeQuery[S](query: Query[S], iniatializing: Boolean): List[S] = {
		val result = entitiesFromStorage(query, iniatializing) ++ entitiesFromCache(query)
		query.orderByClause.map(order =>
			result.sorted(order.ordering)).getOrElse(result)
	}

	def materializeEntity(entityId: String): Entity = {
		val entityClass = EntityHelper.getEntityClassFromId(entityId)
		entityId.intern.synchronized {
			entityInstacesMap(entityClass).getOrElse(entityId, {
				toCache(entityClass, () => createLazyEntity(entityClass, entityId))
			})
		}
	}

	def materializeEntityIfNotDeleted(entityId: String): Option[Entity] = {
		val ret = materializeEntity(entityId)
		if (ret.isDeletedSnapshot)
			None
		else
			Some(ret)
	}

	private def initializeLazyEntityProperties[E <: Entity](entity: E, entityMetadata: EntityMetadata) =
		for (propertyMetadata <- entityMetadata.propertiesMetadata) {
			val typ = propertyMetadata.propertyType
			val field = propertyMetadata.varField
			val ref = new Var(propertyMetadata, entity)
			field.set(entity, ref)
		}

	private def initalizeLazyEntityId[E <: Entity](entity: E, entityMetadata: EntityMetadata, entityId: String) = {
		val idField = entityMetadata.idField
		val ref = new IdVar(entityMetadata.idPropertyMetadata, entity, entityId)
		idField.set(entity, ref)
	}

	private def initalizeLazyEntity[E <: Entity](entity: E, entityMetadata: EntityMetadata, entityId: String) =
		transactional(transient) {
			initializeLazyEntityProperties(entity, entityMetadata)
			initalizeLazyEntityId(entity, entityMetadata, entityId)
			entity.setPersisted
			entity.setNotInitialized
			entity.invariants
		}

	def createLazyEntity[E <: Entity](entityClass: Class[E], entityId: String) = {
		val entity = newInstance[E](entityClass)
		val entityMetadata = EntityHelper.getEntityMetadata(entityClass)
		initalizeLazyEntity(entity, entityMetadata, entityId)
		context.entityMaterialized(entity)
		entity
	}

	def uninitialize(ids: Set[String]) =
		ids.map(id => byId[Entity](id)(manifestClass(EntityHelper.getEntityClassFromId(id)))).flatten.foreach(_.uninitialize)

	def executePendingMassStatements(entity: Entity) =
		for (statement <- context.currentTransactionStatements)
			if (statement.from.entitySources.onlyOne.entityClass == entity.getClass) {
				val entities = List(List(entity))
				executeMassModificationWithEntitySources(statement, entities)
			}

	def initialize(entity: Entity) = {
		val vars = entity.vars.toList
		if (vars.size != 1) {
			val list = produceQuery({ (e: Entity) =>
				where(e :== entity.id) selectList (e.vars.map(toStatementValueRef).toList)
			})(manifestClass(entity.niceClass)).execute(true)
			val row = list.headOption
			if (row.isDefined) {
				val tuple = row.get
				for (i <- 0 until vars.size) {
					val ref = vars(i)
					val value = tuple.productElement(i)
					ref.setRefContent(Option(value))
				}
				executePendingMassStatements(entity)
			} else entity.delete
		}
	}

	def executeQueryWithEntitySources[S](query: Query[S], entitySourcesInstancesCombined: List[List[Entity]]): List[S] = {
		val result = ListBuffer[S]()
		for (entitySourcesInstances <- entitySourcesInstancesCombined)
			result ++= executeQueryWithEntitySourcesMap(query, entitySourceInstancesMap(query.from, entitySourcesInstances))
		result.toList
	}

	def executeMassModificationWithEntitySources[S](statement: MassModificationStatement, entitySourcesInstancesCombined: List[List[Entity]]) =
		for (entitySourcesInstances <- entitySourcesInstancesCombined)
			executeMassModificationWithEntitySourcesMap(statement, entitySourceInstancesMap(statement.from, entitySourcesInstances))

	def entitySourceInstancesMap(from: From, entitySourcesInstances: List[Entity]) = {
		val result = ListBuffer[(EntitySource, Entity)]()
		var i = 0
		for (entitySourceInstance <- entitySourcesInstances) {
			result += (from.entitySources(i) -> entitySourceInstance)
			i += 1
		}
		result.toMap
	}

	def executeQueryWithEntitySourcesMap[S](query: Query[S], entitySourceInstancesMap: Map[EntitySource, Entity]): List[S] = {
		val satisfyWhere = executeCriteria(query.where.value)(entitySourceInstancesMap)
		if (satisfyWhere) {
			List(executeSelect[S](query.select.values: _*)(entitySourceInstancesMap))
		} else
			List[S]()
	}

	def executeMassModificationWithEntitySourcesMap[S](statement: MassModificationStatement, entitySourceInstancesMap: Map[EntitySource, Entity]): Unit = {
		val satisfyWhere = executeCriteria(statement.where.value)(entitySourceInstancesMap)
		if (satisfyWhere)
			statement match {
				case update: MassUpdateStatement =>
					executeUpdateAssignment(update.assignments: _*)(entitySourceInstancesMap)
				case delete: MassDeleteStatement =>
					entitySourceInstancesMap.values.foreach(_.delete)
			}
	}

	def executeSelect[S](values: StatementSelectValue[_]*)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): S = {
		val list = ListBuffer[Any]()
		for (value <- values)
			list += executeStatementSelectValue(value)
		CollectionUtil.toTuple(list)
	}

	def executeUpdateAssignment[S](updateAssignments: UpdateAssignment*)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Unit = {
		for (assignment <- updateAssignments) {
			val valueToSet = executeStatementValue(assignment.value)
			assignment.assignee match {
				case prop: StatementEntitySourcePropertyValue[_] =>
					val ref = prop.propertyPathVars.onlyOne("Update statement does not support nested properties")
					val entity = entitySourceInstancesMap.values.onlyOne("Update statement does not support nested properties")
					val entityRef = entity.varNamed(ref.name)
					entityRef := valueToSet
				case other =>
					throw new IllegalStateException("An update statement should have a entity property in the left side.")
			}
		}
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

	def executeCompositeOperatorCriteria(criteria: CompositeOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean = {
		val a = executeStatementValue(criteria.valueA)
		val b = executeStatementValue(criteria.valueB)
		criteria.operator match {
			case operator: IsEqualTo =>
				equals(a, b)
			case operator: IsNotEqualTo =>
				!equals(a, b)
			case operator: IsGreaterThan =>
				(a != null && b != null) &&
					compare(a, b) > 0
			case operator: IsLessThan =>
				(a != null && b != null) &&
					compare(a, b) < 0
			case operator: IsGreaterOrEqualTo =>
				(a != null && b != null) &&
					compare(a, b) >= 0
			case operator: IsLessOrEqualTo =>
				(a != null && b != null) &&
					compare(a, b) <= 0
			case operator: Matcher =>
				val matcher = b.asInstanceOf[String]
				(a != null && matcher != null) &&
					a.toString.matches(matcher)
		}
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
					val valueA = valueForEquals(a)
					val valueB = valueForEquals(b)
					valueA.equals(valueB)
			}

	def valueForEquals(a: Any) =
		a match {
			case entity: Entity =>
				entity.id
			case instant: AbstractInstant =>
				instant.getMillis
			case other =>
				other
		}

	def executeSimpleOperatorCriteria(criteria: SimpleOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		criteria.operator match {
			case operator: IsNull =>
				executeStatementValue(criteria.valueA) == null
			case operator: IsNotNull =>
				executeStatementValue(criteria.valueA) != null
		}

	def executeBooleanOperatorCriteria(criteria: BooleanOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		criteria.operator match {
			case operator: And =>
				executeStatementBooleanValue(criteria.valueA) && executeStatementBooleanValue(criteria.valueB)
			case operator: Or =>
				executeStatementBooleanValue(criteria.valueA) || executeStatementBooleanValue(criteria.valueB)
		}

	def executeStatementBooleanValue(value: StatementBooleanValue)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Boolean =
		value match {
			case value: SimpleStatementBooleanValue =>
				value.value
			case value: Criteria =>
				executeCriteria(value)
		}

	def executeStatementValue(value: StatementValue)(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any =
		value match {
			case value: Criteria =>
				executeCriteria(value)
			case value: StatementBooleanValue =>
				executeStatementBooleanValue(value)
			case value: StatementSelectValue[_] =>
				executeStatementSelectValue(value)
			case null =>
				null
		}

	def executeStatementSelectValue(value: StatementSelectValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any =
		value match {
			case value: StatementEntityValue[_] =>
				executeStatementEntityValue(value)
			case value: SimpleValue[_] =>
				value.anyValue
		}

	def executeStatementEntityValue(value: StatementEntityValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any =
		value match {
			case value: StatementEntityInstanceValue[_] =>
				value.entity
			case value: StatementEntitySourceValue[_] =>
				executeStatementEntitySourceValue(value)
		}

	def executeStatementEntitySourceValue(value: StatementEntitySourceValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, Entity]): Any = {
		val entity = entitySourceInstancesMap.get(value.entitySource).get
		value match {
			case value: StatementEntitySourcePropertyValue[_] =>
				entityPropertyPathRef(entity, value.propertyPathVars.toList)
			case value: StatementEntitySourceValue[_] =>
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

	def entityProperty[T](entity: Entity, propertyName: String) = {
		var ref = entity.varNamed(propertyName)
		(if (ref == null)
			null
		else
			ref.getValue).asInstanceOf[T]
	}

	def entitySourceInstancesCombined(isMassModification: Boolean, from: From) =
		CollectionUtil.combine(entitySourceInstances(isMassModification, from.entitySources: _*))

	def entitySourceInstances(isMassModification: Boolean, entitySources: EntitySource*) =
		for (entitySource <- entitySources)
			yield fromCache(entitySource.entityClass, isMassModification)

}
