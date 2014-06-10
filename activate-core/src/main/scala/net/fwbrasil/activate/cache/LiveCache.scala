package net.fwbrasil.activate.cache

import language.existentials
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
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
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
import net.fwbrasil.activate.util.Reflection._
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
import net.fwbrasil.activate.entity.EntityMetadata
import org.joda.time.base.AbstractInstant
import net.fwbrasil.activate.entity.ListEntityValue
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.ReferenceListEntityValue
import scala.concurrent.Future
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import net.fwbrasil.activate.statement.FunctionApply
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.entity.StringEntityValue
import net.fwbrasil.activate.statement.ToLowerCase
import com.google.common.collect.MapMaker
import java.util.concurrent.ConcurrentMap
import CacheType._
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.statement.In
import net.fwbrasil.activate.statement.ListValue
import net.fwbrasil.activate.statement.NotIn
import org.joda.time.DateTime

class LiveCache(
    val context: ActivateContext,
    cacheType: CacheType,
    customCaches: List[CustomCache[BaseEntity]]) extends Logging {

    info("Initializing live cache for context " + context.contextName)

    import context._

    val cache =
        EntityHelper.allConcreteEntityClasses
            .map((_, cacheType.mapMaker.makeMap[Any, BaseEntity]))
            .toMap

    def reinitialize =
        logInfo("live cache reinitialize") {
            cache.values.foreach(_.clear)
            customCaches.foreach(_.clear)
        }

    def byId[E <: BaseEntity: Manifest](id: BaseEntity#ID): Option[E] =
        Option(entityInstacesMap[E].get(id))

    def contains[E <: BaseEntity](entity: E) =
        entityInstacesMap(entity.getClass)
            .containsKey(entity.id)

    def delete(entity: BaseEntity): Unit =
        delete(entity.id)

    def delete(entityId: BaseEntity#ID) =
        if (!storage.isMemoryStorage)
            customCaches.foreach(_.remove(entityId))

    def remove(entity: BaseEntity): Unit =
        entityInstacesMap(entity.niceClass).remove(entity.id)

    def toCache[E <: BaseEntity](entity: E): E =
        toCache(entity.niceClass, entity)

    def toCache[E <: BaseEntity](entityClass: Class[E], entity: E): E = {
        val map = entityInstacesMap(entityClass)
        map.put(entity.id, entity)

        entity
    }

    def fromCache[E <: BaseEntity](entityClass: Class[E]) = {
        import scala.collection.JavaConversions._
        val map = entityInstacesMap(entityClass)
        map.values.toList.filter(_.isInitialized)
    }

    def entityInstacesMap[E <: BaseEntity: Manifest]: ConcurrentMap[Any, E] =
        entityInstacesMap(manifestToClass(manifest[E]))

    def entityInstacesMap[E <: BaseEntity](entityClass: Class[E]): ConcurrentMap[Any, E] = {
        cache(entityClass.asInstanceOf[Class[BaseEntity]])
    }.asInstanceOf[ConcurrentMap[Any, E]]

    def executeMassModification(statement: MassModificationStatement) = {
        val entities =
            entitySourceInstancesCombined(statement.from)
                .filter(list => list.head.isInitialized && !list.head.isDeleted)
        executeMassModificationWithEntitySources(statement, entities)
    }

    def entitiesFromCache[S](query: Query[S]) = {
        val isMemoryStorage = storageFor(query).isMemoryStorage
        val dirtyEntities = dirtyEntitiesFromTransaction(classOf[BaseEntity])
        if (isMemoryStorage || dirtyEntities.nonEmpty) {
            val entities =
                entitySourceInstancesCombined(query.from)
            val filteredWithDeletedEntities =
                if (isMemoryStorage)
                    entities
                else
                    entities.filter(_.find(entity => dirtyEntities.contains(entity.id)).isDefined)
            val filteredWithoutDeletedEntities =
                filteredWithDeletedEntities.filter(_.find(_.isDeleted).isEmpty)
            val rows = executeQueryWithEntitySources(query, filteredWithoutDeletedEntities)
            (rows, filteredWithDeletedEntities.filter(_.find(_.isPersisted).isDefined))
        } else
            (List(), List())
    }

    def dirtyEntitiesFromTransaction[E <: BaseEntity](entityClass: Class[E]) = {
        import scala.collection.JavaConversions._
        val transaction = context.transactionManager.getRequiredActiveTransaction
        transaction.refsSnapshot.collect {
            case (ref: Var[_], snapshot) if (
                snapshot.isWrite && entityClass.isAssignableFrom(ref.outerEntityClass) &&
                ref.outerEntity.isInitialized) =>
                val entity = ref.outerEntity
                entity.id -> entity.asInstanceOf[E]
        }.toMap
    }

    def entitiesFromStorage[S](query: Query[S], entitiesReadFromCache: List[List[BaseEntity]]) =
        materializeLines(
            storageFor(query)
                .fromStorage(query, entitiesReadFromCache))

    private def entitiesFromStorageAsync[S](query: Query[S], entitiesReadFromCache: List[List[BaseEntity]])(implicit texctx: TransactionalExecutionContext) =
        storageFor(query)
            .fromStorageAsync(query, entitiesReadFromCache)(texctx)
            .map(materializeLines)(texctx)

    def materialize(value: EntityValue[_]) =
        value match {
            case value: ReferenceListEntityValue[_] =>
                value.value.map(_.map(_.flatMap(id => context.byId(id, initialized = false)(value.m.asInstanceOf[Manifest[BaseEntity]])).orNull)).orNull
            case value: EntityInstanceReferenceValue[_] =>
                if (value.entityClass.isConcreteClass)
                    value.value.map(id => liveCache.materializeEntity(id, value.entityClass.asInstanceOf[Class[BaseEntity]])).orNull
                else
                    value.value.flatMap(id => context.byId(id, value.entityClass.asInstanceOf[Class[BaseEntity]], initialized = false)).orNull
            case other: EntityValue[_] =>
                other.value.getOrElse(null)
        }

    def executeQuery[S](query: Query[S], onlyInMemory: Boolean = false): List[List[Any]] = {
        val (fromCache, entitiesReadFromCache) = entitiesFromCache(query)
        val fromDatabase =
            if (onlyInMemory)
                List()
            else
                entitiesFromStorage(query, entitiesReadFromCache)
        mergeResults(query, fromCache, fromDatabase)
    }

    def mergeResults(
        query: Query[_],
        fromCache: List[List[Any]],
        fromDatabase: List[List[Any]]) =
        query match {
            case query: LimitedOrderedQuery[_] =>
                val ordering = query._orderBy.ordering
                val fromCacheOrdered = fromCache.sorted(ordering)
                val withOffset =
                    fromDatabase match {
                        case Nil =>
                            query.offsetOption.map(fromCacheOrdered.drop)
                                .getOrElse(fromCacheOrdered)
                        case head :: tail =>
                            val ordering = query._orderBy.ordering
                            val fromCacheFiltered =
                                fromCacheOrdered
                                    .dropWhile(e => ordering.compare(e, head) < 0)
                            val result = fromDatabase ++ fromCacheFiltered
                            result.sorted(ordering)
                    }
                withOffset.take(query.limit)
            case other =>
                fromDatabase ++ fromCache
        }

    def executeQueryAsync[S](query: Query[S])(implicit texctx: TransactionalExecutionContext): Future[List[List[Any]]] = {
        Future(entitiesFromCache(query))(texctx).flatMap {
            tuple =>
                val (rowsFromCache, entitiesReadFromCache) = tuple
                val future = entitiesFromStorageAsync(query, entitiesReadFromCache)
                future.map(_ ++ rowsFromCache)(context.ectx)
        }(context.ectx)
    }

    def materializeEntity(entityId: BaseEntity#ID, entityClass: Class[BaseEntity]): BaseEntity = {
        val map = entityInstacesMap(entityClass)
        val entity = map.get(entityId)
        if (entity == null) {
            synchronizeOn(entityId) {
                val entity = map.get(entityId)
                if (entity == null) {
                    val entity = createLazyEntity(entityClass, entityId)
                    require(map.put(entityId, entity) == null)
                    addToCache(entity)
                    entity
                } else
                    entity
            }
        } else
            entity
    }

    private def synchronizeOn[R](entityId: BaseEntity#ID)(f: => R) =
        entityId match {
            case entityId: String =>
                entityId.intern.synchronized(f)
            case entityId: Long =>
                entityId.toString.intern.synchronized(f)
            case entityId =>
                entityId.synchronized(f)
        }

    def materializeEntity(entityId: String): BaseEntity = {
        val entityClass = EntityHelper.getEntityClassFromId(entityId)
        materializeEntity(entityId.asInstanceOf[BaseEntity#ID], entityClass)
    }

    private def initializeLazyEntityProperties[E <: BaseEntity](entity: E, entityMetadata: EntityMetadata) =
        for (propertyMetadata <- entityMetadata.propertiesMetadata) {
            val typ = propertyMetadata.propertyType
            val field = propertyMetadata.varField
            val ref = new Var(propertyMetadata, entity, false)
            field.set(entity, ref)
        }

    private def initalizeLazyEntityId[E <: BaseEntity](entity: E, entityMetadata: EntityMetadata, entityId: BaseEntity#ID) = {
        val idField = entityMetadata.idField
        val ref = new IdVar(entityMetadata.idPropertyMetadata, entity, entityId)
        idField.set(entity, ref)
    }

    private def initalizeLazyEntity[E <: BaseEntity](entity: E, entityMetadata: EntityMetadata, entityId: BaseEntity#ID) =
        transactional(shadow) {
            initializeLazyEntityProperties(entity, entityMetadata)
            initalizeLazyEntityId(entity, entityMetadata, entityId)
            entity.setPersisted
            entity.setNotInitialized
            entity.buildVarsMap
            entity.invariants
            entity.initializeListeners
        }

    def createLazyEntity[E <: BaseEntity](entityClass: Class[E], entityId: BaseEntity#ID) = {
        val entity = newInstance[E](entityClass)
        val entityMetadata = entity.entityMetadata
        initalizeLazyEntity(entity, entityMetadata, entityId)
        context.entityMaterialized(entity)
        entity
    }

    def reloadEntities(ids: Set[(BaseEntity#ID, Class[BaseEntity])]) =
        (for ((id, clazz) <- ids) yield byId[BaseEntity](id)(manifestClass(clazz)))
            .flatten.map(_.reloadFromDatabase)

    def executePendingMassStatements(entity: BaseEntity) =
        for (statement <- context.currentTransactionStatements)
            if (statement.from.entitySources.onlyOne.entityClass == entity.getClass) {
                val entities = List(List(entity))
                executeMassModificationWithEntitySources(statement, entities)
            }

    def initializeEntityIfNecessary[E <: BaseEntity: Manifest](values: Map[String, Any]) = {
        val id = values("id").asInstanceOf[E#ID]
        val entity = context.byId[E](id).get
        entity.synchronized {
            if (!entity.isInitialized) {
                val map = values.map(tuple => (entity.varNamed(tuple._1), tuple._2)).toMap
                setEntityValues(entity, map)
                entity.setInitialized
            }
        }
        entity
    }

    def loadFromDatabase(entity: BaseEntity): Unit =
        initializeEntity(loadRowFromDatabase(entity), entity, transient)

    def asyncLoadFromDatabase(entity: BaseEntity)(implicit tctx: TransactionalExecutionContext) =
        asyncLoadRowFromDatabase(entity, tctx).map(initializeEntity(_, entity, shadow))(tctx)

    private def initializeEntity(rowOption: Option[List[Any]], entity: BaseEntity, deletePropagation: Propagation) = {
        entity.lastVersionValidation = DateTime.now.getMillis
        rowOption.map { row =>
            val vars = entity.vars.toList.filter(p => !p.isTransient)
            setEntityValues(entity, vars.zip(row).toMap)
            Some(entity)
        }.getOrElse {
            transactional(deletePropagation) {
                entity.deleteWithoutInitilize
            }
            None
        }
    }

    def setEntityValues(entity: BaseEntity, values: Map[Var[Any], Any]): Unit = {
        for ((ref, value) <- values)
            ref.setRefContent(Option(value))
        executePendingMassStatements(entity)
    }

    def executeQueryWithEntitySources[S](query: Query[S], entitySourcesInstancesCombined: List[List[BaseEntity]]): List[List[Any]] = {
        val result = ListBuffer[List[Any]]()
        for (entitySourcesInstances <- entitySourcesInstancesCombined)
            result ++= executeQueryWithEntitySourcesMap(query, entitySourceInstancesMap(query.from, entitySourcesInstances))
        result.toList
    }

    def executeMassModificationWithEntitySources[S](statement: MassModificationStatement, entitySourcesInstancesCombined: List[List[BaseEntity]]) =
        for (entitySourcesInstances <- entitySourcesInstancesCombined)
            executeMassModificationWithEntitySourcesMap(statement, entitySourceInstancesMap(statement.from, entitySourcesInstances))

    def entitySourceInstancesMap(from: From, entitySourcesInstances: List[BaseEntity]) = {
        val result = ListBuffer[(EntitySource, BaseEntity)]()
        var i = 0
        for (entitySourceInstance <- entitySourcesInstances) {
            result += (from.entitySources(i) -> entitySourceInstance)
            i += 1
        }
        result.toMap
    }

    def executeQueryWithEntitySourcesMap[S](query: Query[S], entitySourceInstancesMap: Map[EntitySource, BaseEntity]): List[List[Any]] = {
        val satisfyWhere = executeCriteria(query.where.valueOption)(entitySourceInstancesMap)
        if (satisfyWhere) {
            List(executeSelect[S](query.select.values: _*)(entitySourceInstancesMap))
        } else
            List[List[Any]]()
    }

    def executeMassModificationWithEntitySourcesMap[S](statement: MassModificationStatement, entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Unit = {
        val satisfyWhere = executeCriteria(statement.where.valueOption)(entitySourceInstancesMap)
        if (satisfyWhere)
            statement match {
                case update: MassUpdateStatement =>
                    executeUpdateAssignment(update.assignments: _*)(entitySourceInstancesMap)
                case delete: MassDeleteStatement =>
                    entitySourceInstancesMap.values.foreach(_.delete)
            }
    }

    def executeSelect[S](values: StatementSelectValue*)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): List[Any] = {
        val list = ListBuffer[Any]()
        for (value <- values)
            list += executeStatementSelectValue(value)
        list.toList
    }

    def executeUpdateAssignment[S](updateAssignments: UpdateAssignment*)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Unit = {
        for (assignment <- updateAssignments) {
            val valueToSet = executeStatementValue(assignment.value)
            assignment.assignee match {
                case prop: StatementEntitySourcePropertyValue =>
                    val ref = prop.propertyPathVars.onlyOne("Update statement does not support nested properties")
                    val entity = entitySourceInstancesMap.values.onlyOne("Update statement does not support nested properties")
                    val entityRef = entity.varNamed(ref.name)
                    entityRef := valueToSet
                case other =>
                    throw new IllegalStateException("An update statement should have a entity property in the left side.")
            }
        }
    }

    def executeCriteria(criteriaOption: Option[Criteria])(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Boolean =
        criteriaOption match {
            case Some(criteria: Criteria) =>
                executeCriteria(criteria)
            case None =>
                true
        }

    def executeCriteria(criteria: Criteria)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Boolean =
        criteria match {
            case criteria: BooleanOperatorCriteria =>
                executeBooleanOperatorCriteria(criteria)
            case criteria: SimpleOperatorCriteria =>
                executeSimpleOperatorCriteria(criteria)
            case criteria: CompositeOperatorCriteria =>
                executeCompositeOperatorCriteria(criteria)
        }

    def executeCompositeOperatorCriteria(criteria: CompositeOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Boolean = {
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
            case operator: In =>
                b.asInstanceOf[List[_]].contains(a)
            case operator: NotIn =>
                !b.asInstanceOf[List[_]].contains(a)
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
            case entity: BaseEntity =>
                entity.id
            case instant: AbstractInstant =>
                instant.getMillis
            case other =>
                other
        }

    def executeSimpleOperatorCriteria(criteria: SimpleOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Boolean =
        criteria.operator match {
            case operator: IsNull =>
                executeStatementValue(criteria.valueA) == null
            case operator: IsNotNull =>
                executeStatementValue(criteria.valueA) != null
        }

    def executeBooleanOperatorCriteria(criteria: BooleanOperatorCriteria)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Boolean =
        criteria.operator match {
            case operator: And =>
                executeStatementBooleanValue(criteria.valueA) && executeStatementBooleanValue(criteria.valueB)
            case operator: Or =>
                executeStatementBooleanValue(criteria.valueA) || executeStatementBooleanValue(criteria.valueB)
        }

    def executeStatementBooleanValue(value: StatementBooleanValue)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Boolean =
        value match {
            case value: SimpleStatementBooleanValue =>
                value.value
            case value: Criteria =>
                executeCriteria(Some(value))
        }

    def executeStatementValue(value: StatementValue)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Any =
        value match {
            case value: Criteria =>
                executeCriteria(Some(value))
            case value: StatementBooleanValue =>
                executeStatementBooleanValue(value)
            case value: StatementSelectValue =>
                executeStatementSelectValue(value)
            case value: ListValue[_] =>
                value.fList()
            case null =>
                null
        }

    def executeFunctionApply(apply: FunctionApply[_])(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Any =
        apply match {
            case value: ToUpperCase =>
                executeStatementSelectValue(value.value).asInstanceOf[String].toUpperCase()
            case value: ToLowerCase =>
                executeStatementSelectValue(value.value).asInstanceOf[String].toLowerCase()
        }

    def executeStatementSelectValue(value: StatementSelectValue)(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Any =
        value match {
            case value: FunctionApply[_] =>
                executeFunctionApply(value)
            case value: StatementEntityValue[_] =>
                executeStatementEntityValue(value)
            case value: SimpleValue[_] =>
                value.anyValue
        }

    def executeStatementEntityValue(value: StatementEntityValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Any =
        value match {
            case value: StatementEntityInstanceValue[_] =>
                value.entity
            case value: StatementEntitySourceValue[_] =>
                executeStatementEntitySourceValue(value)
        }

    def executeStatementEntitySourceValue(value: StatementEntitySourceValue[_])(implicit entitySourceInstancesMap: Map[EntitySource, BaseEntity]): Any = {
        val entity = entitySourceInstancesMap.get(value.entitySource).get
        value match {
            case value: StatementEntitySourcePropertyValue =>
                entityPropertyPathRef(entity, value.propertyPathVars.toList)
            case value: StatementEntitySourceValue[_] =>
                entity
        }
    }

    def entityPropertyPathRef(entity: BaseEntity, propertyPathVars: List[Var[_]]): Any =
        propertyPathVars match {
            case Nil =>
                null
            case propertyVar :: Nil =>
                entityProperty(entity, propertyVar.name)
            case propertyVar :: propertyPath => {
                val property = entityProperty[BaseEntity](entity, propertyVar.name)
                if (property != null)
                    entityPropertyPathRef(
                        property,
                        propertyPath)
                else
                    null
            }
        }

    def entityProperty[T](entity: BaseEntity, propertyName: String) = {
        var ref = entity.varNamed(propertyName)
        (if (ref == null)
            null
        else
            ref.getValue).asInstanceOf[T]
    }

    def entitySourceInstancesCombined(from: From) = {
        for (entitySource <- from.entitySources) {
            val entities = fromCache(entitySource.entityClass)
            for (entity <- entities)
                if (!(entity.isPersisted || entity.isDirty) &&
                    !storageFor(entity.niceClass).isMemoryStorage &&
                    entity.vars.find(_.isCreating).isEmpty)
                    entity.initializeGraph
        }
        CollectionUtil.combine(entitySourceInstances(from.entitySources: _*))
    }

    def entitySourceInstances(entitySources: EntitySource*) =
        for (entitySource <- entitySources)
            yield fromCache(entitySource.entityClass)

    private def loadRowFromDatabase(entity: BaseEntity) =
        entitiesFromStorage(loadRowQuery(entity), List()).headOption

    private def asyncLoadRowFromDatabase(entity: BaseEntity, tcxt: TransactionalExecutionContext) =
        entitiesFromStorageAsync(loadRowQuery(entity), List())(tcxt).map(_.headOption)(tcxt)

    private def loadRowQuery(entity: BaseEntity) =
        produceQuery[Product, BaseEntity, Query[Product]]({ (e: BaseEntity) =>
            where(e :== entity.id)
                .selectList(e.vars.filter(p => !p.isTransient).map(toStatementValueRef).toList)
        })(manifestClass(entity.getClass))

    private def materializeLines(lines: List[List[net.fwbrasil.activate.entity.EntityValue[_]]]): List[List[Any]] =
        for (line <- lines)
            yield for (column <- line)
            yield materialize(column)

    private def addToCache(entity: BaseEntity) =
        if (customCaches.nonEmpty && !context.storageFor(entity.getClass).isMemoryStorage)
            customCaches.foreach(_.add(entity)(context))
}
