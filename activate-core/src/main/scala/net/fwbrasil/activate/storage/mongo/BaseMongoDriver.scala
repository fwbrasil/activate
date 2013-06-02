package net.fwbrasil.activate.storage.mongo

import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.CompositeOperator
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.statement.query.OrderedQuery
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
import net.fwbrasil.activate.storage.marshalling.StorageAddReference
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.ToLowerCase
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex
import net.fwbrasil.activate.util.IdentityHashMap
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import scala.collection.mutable.ListBuffer
import java.util.Date
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import net.fwbrasil.activate.entity.EntityHelper.getEntityName

trait BaseDBObject {
    def put(key: String, obj: Any): Unit
    def get(key: String): Any
}

trait BaseDBList {
    def add(obj: Object)
    def toList: List[Any]
}

trait BaseDBColl[OBJ <: BaseDBObject, RES <: BaseDBCursor[OBJ], WR <: BaseDBWriteResult] {
    def find(query: OBJ): RES
    def find(query: OBJ, select: OBJ): RES
    def remove(query: OBJ): WR
    def update(query: OBJ, update: OBJ): WR
    def updateMulti(query: OBJ, update: OBJ): WR
    def insert(list: List[OBJ]): WR
    def drop: Unit
    def dropIndex(obj: OBJ): Unit
    def rename(newName: String): Unit
    def ensureIndex(obj: OBJ, options: OBJ): Unit
}

trait BaseDBWriteResult {
    def getN: Int
}

trait BaseDBCursor[OBJ <: BaseDBObject] {
	def limit(elems: Int)
	def sort(obj: OBJ)
	def close
    def count: Int
    def toArray: List[OBJ]
}

trait BaseMongoDriver[CON, OBJ <: BaseDBObject, LIST <: BaseDBList with OBJ, RES <: BaseDBCursor[OBJ], WR <: BaseDBWriteResult, COLL <: BaseDBColl[OBJ, RES, WR]]
        extends MarshalStorage[CON] {

    val host: String
    val port: Int = 27017
    val db: String
    val authentication: Option[(String, String)] = None

    def isMemoryStorage = false
    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = false

    protected def newDBObject: OBJ
    protected def newDBList: LIST

    protected def coll(name: String): COLL
    protected def collectionExists(name: String): Boolean
    protected def createCollection(name: String, obj: OBJ): Unit
    protected def collHasIndex(name: String, column: String): Boolean

    private[this] def coll(entity: Entity): COLL =
        coll(entity.getClass)

    private[this] def coll(entityClass: Class[_]): COLL =
        coll(getEntityName(entityClass))

    override def store(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        preVerifyStaleData(updateList ++ deleteList)
        storeStatements(statements)
        storeInserts(insertList)
        storeUpdates(updateList)
        storeDeletes(deleteList)

        None
    }

    private def preVerifyStaleData(
        data: List[(Entity, Map[String, StorageValue])]) = {
        val invalid =
            data.filter(_._2.contains(versionVarName)).filterNot { tuple =>
                val (entity, properties) = tuple
                val query = newDBObject
                query.put("_id", entity.id)
                addVersionCondition(query, properties)
                val result = coll(entity).find(query).count
                result == 1
            }
        if (invalid.nonEmpty)
            staleDataException(invalid.map(_._1.id).toSet)
    }

    private def addVersionCondition(query: BaseDBObject, properties: Map[String, StorageValue]) =
        if (properties.contains(versionVarName)) {
            val nullVersion = newDBObject
            nullVersion.put(versionVarName, null)
            val versionValue = newDBObject
            versionValue.put(versionVarName, getMongoValue(properties(versionVarName)) match {
                case value: Long =>
                    value - 1l
            })
            val versionQuery = newDBList
            versionQuery.add(nullVersion)
            versionQuery.add(versionValue)
            query.put("$or", versionQuery)
        }

    private def storeDeletes(deleteList: List[(Entity, Map[String, StorageValue])]) =
        for ((entity, properties) <- deleteList) {
            val query = newDBObject
            query.put("_id", entity.id)
            addVersionCondition(query, properties)
            val result = coll(entity).remove(query)
            if (result.getN != 1)
                staleDataException(Set(entity.id))
        }

    private def storeUpdates(updateList: List[(Entity, Map[String, StorageValue])]) =
        for ((entity, properties) <- updateList) {
            val query = newDBObject
            query.put("_id", entity.id)
            val set = newDBObject
            for ((name, value) <- properties if (name != "id")) {
                val inner = newDBObject
                set.put(name, getMongoValue(value))
            }
            val update = newDBObject
            update.put("$set", set)
            addVersionCondition(query, properties)
            val result = coll(entity).update(query, update)
            if (result.getN != 1)
                staleDataException(Set(entity.id))
        }

    private def storeInserts(insertList: List[(Entity, Map[String, StorageValue])]) = {
        val insertMap = new IdentityHashMap[Class[_], ListBuffer[OBJ]]()
        for ((entity, properties) <- insertList) {
            val doc = newDBObject
            for ((name, value) <- properties if (name != "id"))
                doc.put(name, getMongoValue(value))
            doc.put("_id", entity.id)
            insertMap.getOrElseUpdate(entity.getClass, ListBuffer()) += doc
        }
        for (entityClass <- insertMap.keys)
            coll(entityClass).insert(insertMap(entityClass).toList)
    }

    private def storeStatements(statements: List[MassModificationStatement]) =
        for (statement <- statements) {
            val (coll, where) = collectionAndWhere(statement.from, statement.where)
            statement match {
                case update: MassUpdateStatement =>
                    val set = newDBObject
                    for (assignment <- update.assignments)
                        set.put(mongoStatementSelectValue(assignment.assignee), getMongoValue(assignment.value))
                    val mongoUpdate = newDBObject
                    mongoUpdate.put("$set", set)
                    coll.updateMulti(where, mongoUpdate)
                case delete: MassDeleteStatement =>
                    coll.remove(where)
            }
        }

    private def getMongoValue(value: StorageValue): Any =
        value match {
            case value: IntStorageValue =>
                value.value.map(_.intValue).getOrElse(null)
            case value: LongStorageValue =>
                value.value.map(_.longValue).getOrElse(null)
            case value: BooleanStorageValue =>
                value.value.map(_.booleanValue).getOrElse(null)
            case value: StringStorageValue =>
                value.value.getOrElse(null)
            case value: FloatStorageValue =>
                value.value.map(_.doubleValue).getOrElse(null)
            case value: DateStorageValue =>
                value.value.getOrElse(null)
            case value: DoubleStorageValue =>
                value.value.map(_.doubleValue).getOrElse(null)
            case value: BigDecimalStorageValue =>
                value.value.map(_.doubleValue).getOrElse(null)
            case value: ListStorageValue =>
                value.value.map { list =>
                    val dbList = newDBList
                    list.foreach(elem => dbList.add(getMongoValue(elem).asInstanceOf[Object]))
                    dbList
                }.orNull
            case value: ByteArrayStorageValue =>
                value.value.getOrElse(null)
            case value: ReferenceStorageValue =>
                value.value.getOrElse(null)
        }

    override def query(queryInstance: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] = {
        val from = queryInstance.from
        val (coll, where) = collectionAndWhere(from, queryInstance.where, entitiesReadFromCache)
        val selectValues = queryInstance.select.values
        val select = querySelect(queryInstance, selectValues)
        val ret = coll.find(where, select)
        orderQueryIfNecessary(queryInstance, ret)
        limitQueryIfNecessary(queryInstance, ret)
        transformResultToTheExpectedTypes(expectedTypes, selectValues, ret)
    }

    def getStorageValue(obj: Any, storageValue: StorageValue): StorageValue = {
        def getValue[T] = Option(obj.asInstanceOf[T])
        storageValue match {
            case value: IntStorageValue =>
                IntStorageValue(getValue[Int])
            case value: LongStorageValue =>
                LongStorageValue(getValue[Long])
            case value: BooleanStorageValue =>
                BooleanStorageValue(getValue[Boolean])
            case value: StringStorageValue =>
                StringStorageValue(getValue[String])
            case value: FloatStorageValue =>
                FloatStorageValue(getValue[Double].map(_.floatValue))
            case value: DateStorageValue =>
                DateStorageValue(getValue[Date])
            case value: DoubleStorageValue =>
                DoubleStorageValue(getValue[Double])
            case value: BigDecimalStorageValue =>
                BigDecimalStorageValue(getValue[Double].map(BigDecimal(_)))
            case value: ListStorageValue =>
                ListStorageValue(getValue[LIST].map { dbList =>
                    dbList.toList.map(elem => getStorageValue(elem, value.emptyStorageValue))
                }, value.emptyStorageValue)
            case value: ByteArrayStorageValue =>
                ByteArrayStorageValue(getValue[Array[Byte]])
            case value: ReferenceStorageValue =>
                ReferenceStorageValue(getValue[String])
        }
    }

    def getValue(obj: BaseDBObject, name: String, storageValue: StorageValue): StorageValue =
        getStorageValue(obj.get(name), storageValue)

    def getValue[T](obj: BaseDBObject, name: String) =
        Option(obj.get(name).asInstanceOf[T])

    def query(values: StatementSelectValue[_]*): Seq[String] =
        for (value <- values)
            yield mongoStatementSelectValue(value)

    def mongoStatementSelectValue(value: StatementSelectValue[_]): String =
        value match {
            case value: ToUpperCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toUpperCase function for queries.")
            case value: ToLowerCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toLowerCase function for queries.")
            case value: StatementEntitySourcePropertyValue[_] =>
                val name = value.propertyPathNames.onlyOne
                if (name == "id")
                    "_id"
                else
                    name
            case value: StatementEntitySourceValue[_] =>
                "_id"
            case other =>
                throw new UnsupportedOperationException("Mongo storage supports only entity properties inside select clause.")
        }

    def query(criteria: Criteria): OBJ = {
        val obj = newDBObject
        criteria match {
            case criteria: BooleanOperatorCriteria =>
                val list = newDBList
                list.add(query(criteria.valueA))
                list.add(query(criteria.valueB))
                val operator = query(criteria.operator)
                obj.put(operator, list)
                obj
            case criteria: CompositeOperatorCriteria =>
                val property = queryEntityProperty(criteria.valueA)
                val value = getMongoValue(criteria.valueB)
                if (criteria.operator.isInstanceOf[IsEqualTo])
                    obj.put(property, value)
                else {
                    val operator = query(criteria.operator)
                    val innerObj = newDBObject
                    innerObj.put(operator, value)
                    obj.put(property, innerObj)
                }
                obj
            case criteria: SimpleOperatorCriteria =>
                val property = queryEntityProperty(criteria.valueA)
                val value = criteria.operator match {
                    case value: IsNull =>
                        null
                    case value: IsNotNull =>
                        val temp = newDBObject
                        temp.put("$ne", null)
                        temp
                }
                obj.put(property, value)
                obj
        }
    }

    def getMongoValue(value: StatementValue): Any =
        value match {
            case value: SimpleStatementBooleanValue =>
                getMongoValue(Marshaller.marshalling(value.value))
            case value: SimpleValue[_] =>
                getMongoValue(Marshaller.marshalling(value.entityValue))
            case value: StatementEntityInstanceValue[_] =>
                getMongoValue(StringStorageValue(Option(value.entityId)))
            case null =>
                null
            case other =>
                throw new UnsupportedOperationException("Mongo storage doesn't support joins.")
        }

    def query(value: StatementBooleanValue): OBJ =
        value match {
            case value: Criteria =>
                query(value)
            case value: SimpleStatementBooleanValue =>
                val list = newDBList
                list.add(value.value.toString)
                list
        }

    def queryEntityProperty(value: StatementValue): String =
        value match {
            case value: ToUpperCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toUpperCase function for queries.")
            case value: ToLowerCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toLowerCase function for queries.")
            case value: StatementEntitySourcePropertyValue[_] =>
                val name = value.propertyPathNames.onlyOne
                if (name == "id")
                    "_id"
                else
                    name
            case value: StatementEntitySourceValue[_] =>
                "_id"
            case other =>
                throw new UnsupportedOperationException("Mongo storage doesn't support joins.")
        }

    def query(operator: CompositeOperator): String =
        operator match {
            case operator: And =>
                "$and"
            case operator: Or =>
                "$or"
            case operator: IsGreaterOrEqualTo =>
                "$gte"
            case operator: IsGreaterThan =>
                "$gt"
            case operator: IsLessOrEqualTo =>
                "$lte"
            case operator: IsLessThan =>
                "$lt"
            case operator: Matcher =>
                "$regex"
            case operator: IsNotEqualTo =>
                "$ne"
            case operator: IsEqualTo =>
                throw new UnsupportedOperationException("Mongo doesn't have $eq operator yet (https://jira.mongodb.org/browse/SERVER-1367).")
        }

    override def migrateStorage(action: ModifyStorageAction): Unit =
        action match {
            case action: StorageCreateTable =>
                if (!action.ifNotExists || !collectionExists(action.tableName))
                    createCollection(action.tableName, newDBObject)
            case action: StorageRenameTable =>
                coll(action.oldName).rename(action.newName)
            case action: StorageRemoveTable =>
                coll(action.name).drop
            case action: StorageAddColumn =>
            // Do nothing!
            case action: StorageRenameColumn =>
                val update = newDBObject
                val updateInner = newDBObject
                updateInner.put(action.oldName, action.column.name)
                update.put("$rename", updateInner)
                coll(action.tableName).update(newDBObject, update)
            case action: StorageRemoveColumn =>
                val update = newDBObject
                val updateInner = newDBObject
                updateInner.put(action.name, 1)
                update.put("$unset", updateInner)
                coll(action.tableName).update(newDBObject, update)
            case action: StorageAddIndex =>
                val obj = newDBObject
                obj.put(action.columnName, 1)
                val options = newDBObject
                if (action.unique)
                    options.put("unique", true)
                if (!action.ifNotExists || !collHasIndex(action.tableName, action.columnName))
                    coll(action.tableName).ensureIndex(obj, options)
            case action: StorageRemoveIndex =>
                val obj = newDBObject
                obj.put(action.columnName, 1)
                if (!action.ifExists || collHasIndex(action.tableName, action.columnName))
                    coll(action.tableName).dropIndex(obj)
            case action: StorageAddReference =>
            // Do nothing!
            case action: StorageRemoveReference =>
            // Do nothing!
            case action: StorageCreateListTable =>
            // Do nothing!
            case action: StorageRemoveListTable =>
            // Do nothing!
        }

    private def collectionAndWhere(from: From, where: Where, entitiesReadFromCache: List[List[Entity]] = List()) = {
        val baseWhere = query(where.value)
        val mongoWhere =
            if (entitiesReadFromCache.nonEmpty) {
                val where = newDBObject
                val andConditions = newDBList
                andConditions.add(baseWhere)
                val fromCacheIds = newDBList
                for (list <- entitiesReadFromCache)
                    fromCacheIds.add(list.head.id)
                val fromCacheIdsCondition = newDBObject
                val fromCacheIdsNinCondition = newDBObject
                fromCacheIdsNinCondition.put("$nin", fromCacheIds)
                fromCacheIdsCondition.put("_id", fromCacheIdsNinCondition)
                andConditions.add(fromCacheIdsCondition)
                where.put("$and", andConditions)
                where
            } else {
                baseWhere
            }
        val entitySource = from.entitySources.onlyOne("Mongo storage supports only simple queries (only one 'from' entity and without nested properties)")
        val mongoCollection = coll(entitySource.entityClass)
        (mongoCollection, mongoWhere)
    }

    private def limitQueryIfNecessary(queryInstance: Query[_], ret: RES) =
        queryInstance match {
            case q: LimitedOrderedQuery[_] =>
                ret.limit(q.limit)
            case other =>
        }

    private def orderQueryIfNecessary(queryInstance: Query[_], ret: RES) =
        queryInstance match {
            case q: OrderedQuery[_] =>
                val order = newDBObject
                for (criteria <- q.orderByClause.get.criterias) {
                    val property = mongoStatementSelectValue(criteria.value)
                    val direction =
                        if (criteria.direction == orderByAscendingDirection)
                            1
                        else
                            -1
                    order.put(property, direction)
                }
                ret.sort(order)
            case other =>
        }

    private def transformResultToTheExpectedTypes(expectedTypes: List[StorageValue], selectValues: Seq[StatementSelectValue[_]], ret: RES) = {
        try {
            val rows = ret.toArray
            (for (row <- rows) yield (for (i <- 0 until selectValues.size) yield {
                selectValues(i) match {
                    case value: SimpleValue[_] =>
                        expectedTypes(i)
                    case other =>
                        getValue(row, mongoStatementSelectValue(other), expectedTypes(i))
                }
            }).toList).toList
        } finally
            ret.close
    }

    private def querySelect(queryInstance: Query[_], selectValues: Seq[StatementSelectValue[_]]) = {
        val select = newDBObject
        for (value <- selectValues)
            if (!value.isInstanceOf[SimpleValue[_]])
                select.put(mongoStatementSelectValue(value), 1)
        select
    }

}