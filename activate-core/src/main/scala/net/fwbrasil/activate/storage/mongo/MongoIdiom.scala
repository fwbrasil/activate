package net.fwbrasil.activate.storage.mongo

import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import scala.collection.mutable.{ Map => MutableMap }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.CompositeOperator
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.statement.ToLowerCase
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.util.IdentityHashMap
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.query.Select
import net.fwbrasil.activate.statement.query.OrderedQuery
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import java.util.Date

object mongoIdiom {

    def findStaleDataQueries(
        data: List[(Entity, Map[String, StorageValue])]) = {
        for ((entity, properties) <- data.filter(_._2.contains(versionVarName))) yield {
            val query = versionConditionStale(entity.id, properties)
            val select = newObject("_id" -> 1)
            (entity, query, select)
        }
    }

    def versionConditionOrEmpty(
        properties: Map[String, StorageValue]) =
        if (properties.contains(versionVarName))
            versionCondition(properties)
        else
            Map()

    def versionCondition(
        properties: Map[String, StorageValue]) = {
        val versionIsNull = newObject(versionVarName -> null)
        val isTheExcpectedVersion = newObject(versionVarName -> expectedVersion(properties))
        newObject("$or" -> newList(versionIsNull, isTheExcpectedVersion))
    }

    def versionConditionStale(
        id: String,
        properties: Map[String, StorageValue]) = {
        val entityId = newObject("_id" -> id)
        val versionIsNotNull = newObject(versionVarName -> newObject("$ne" -> null))
        val isntTheExcpectedVersion = newObject(versionVarName -> newObject("$ne" -> expectedVersion(properties)))
        newObject("$and" -> newList(entityId, versionIsNotNull, isntTheExcpectedVersion))
    }

    def collectionClass(from: From) =
        from.entitySources.onlyOne(
            "Mongo storage supports only simple queries (only one 'from' entity and without nested properties)")
            .entityClass

    def toQuery(query: Query[_], entitiesReadFromCache: List[List[Entity]]) =
        (removeEntitiesReadFromCache(toQueryWhere(query.where), entitiesReadFromCache),
            toQuerySelect(query.select))

    def toQueryOrder(query: Query[_]): Map[String, Any] =
        query match {
            case q: OrderedQuery[_] =>
                (for (criteria <- q.orderByClause.get.criterias) yield {
                    val property = mongoStatementSelectValue(criteria.value)
                    val direction =
                        if (criteria.direction == orderByAscendingDirection)
                            1
                        else
                            -1
                    property -> direction
                }).toMap
            case other =>
                Map()
        }

    def removeEntitiesReadFromCache(baseWhere: Map[String, Any], entitiesReadFromCache: List[List[Entity]]) =
        if (entitiesReadFromCache.nonEmpty) {
            val idsList = newList(entitiesReadFromCache.map(_.head.id): _*)
            val idsCondition = newObject("_id" -> newObject("$nin" -> idsList))
            newObject("$and" -> newList(baseWhere, idsCondition))
        } else {
            baseWhere
        }

    def toQueryWhere(where: Where) =
        where.valueOption.map(toQueryCriteria).getOrElse(newObject())

    def toQuerySelect(select: Select) =
        (for (value <- select.values if (!value.isInstanceOf[SimpleValue[_]])) yield mongoStatementSelectValue(value) -> 1).toMap

    def toQueryUpdate(update: MassUpdateStatement) = {
        val assignments = update.assignments.map(assignment =>
            mongoStatementSelectValue(assignment.assignee) -> getMongoValue(assignment.value))
        newObject("$set" -> newObject(assignments: _*))
    }

    def transformResultToTheExpectedTypes[DOC](
        expectedTypes: List[StorageValue],
        selectValues: Seq[StatementSelectValue],
        rows: List[DOC],
        rowToColumn: (DOC, String) => Any,
        fromDBList: Any => List[Any]) =
        for (row <- rows) yield {
            (for (i <- 0 until selectValues.size) yield {
                selectValues(i) match {
                    case value: SimpleValue[_] =>
                        expectedTypes(i)
                    case other =>
                        getStorageValue(
                            rowToColumn(row, mongoStatementSelectValue(other)),
                            expectedTypes(i),
                            fromDBList)
                }
            }).toList
        }

    def getStorageValue(obj: Any, storageValue: StorageValue, fromDBList: Any => List[Any]): StorageValue = {
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
                val listOption = Option(obj).map(fromDBList)
                ListStorageValue(listOption.map { dbList =>
                    dbList.map(elem => getStorageValue(elem, value.emptyStorageValue, fromDBList)).toList
                }, value.emptyStorageValue)
            case value: ByteArrayStorageValue =>
                ByteArrayStorageValue(getValue[Array[Byte]])
            case value: ReferenceStorageValue =>
                ReferenceStorageValue(getValue[String])
        }
    }

    def toInsertMap(insertList: List[(Entity, Map[String, StorageValue])]) = {
        val insertMap = new IdentityHashMap[Class[_], ListBuffer[Map[String, Any]]]()
        for ((entity, properties) <- insertList) {
            val values =
                for (
                    (name, value) <- properties if (name != "id")
                ) yield (name -> getMongoValue(value))
            val doc = newObject("_id" -> entity.id) ++ values
            insertMap.getOrElseUpdate(entity.getClass, ListBuffer()) += doc
        }
        insertMap
    }

    def toUpdate(entity: Entity, properties: Map[String, StorageValue]) = {
        val query = newObject("_id" -> entity.id) ++ versionConditionOrEmpty(properties)
        val toSet =
            (for ((name, value) <- properties if (name != "id")) yield name -> getMongoValue(value)).toMap
        (query, newObject("$set" -> toSet))
    }

    def toDelete(entity: Entity, properties: Map[String, StorageValue]) =
        newObject("_id" -> entity.id) ++ versionConditionOrEmpty(properties)

    def renameColumn(oldName: String, newName: String) =
        newObject("$rename" -> newObject(oldName -> newName))

    def removeColumn(name: String) =
        newObject("$unset" -> newObject(name -> 1))

    def toQueryCriteria(criteria: Criteria): Map[String, Any] = {
        criteria match {
            case criteria: BooleanOperatorCriteria =>
                val list =
                    newList(toQueryStatementBooleanValue(criteria.valueA),
                        toQueryStatementBooleanValue(criteria.valueB))
                val operator = toQueryOperator(criteria.operator)
                newObject(operator -> list)
            case criteria: CompositeOperatorCriteria =>
                val property = queryEntityProperty(criteria.valueA)
                val value = getMongoValue(criteria.valueB)
                if (criteria.operator.isInstanceOf[IsEqualTo])
                    newObject(property -> value)
                else {
                    val operator = toQueryOperator(criteria.operator)
                    val innerObj = newObject(operator -> value)
                    newObject(property -> innerObj)
                }
            case criteria: SimpleOperatorCriteria =>
                val property = queryEntityProperty(criteria.valueA)
                val value =
                    criteria.operator match {
                        case value: IsNull =>
                            null
                        case value: IsNotNull =>
                            newObject("$ne" -> null)
                    }
                newObject(property -> value)
        }
    }

    private def mongoStatementSelectValue(value: StatementSelectValue): String =
        value match {
            case value: ToUpperCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toUpperCase function for queries.")
            case value: ToLowerCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toLowerCase function for queries.")
            case value: StatementEntitySourcePropertyValue =>
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

    private def getMongoValue(value: StatementValue): Any =
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

    private def queryEntityProperty(value: StatementValue): String =
        value match {
            case value: ToUpperCase =>
                unsupported("Mongo storage doesn't support the toUpperCase function for queries.")
            case value: ToLowerCase =>
                unsupported("Mongo storage doesn't support the toLowerCase function for queries.")
            case value: StatementEntitySourcePropertyValue =>
                val name = value.propertyPathNames.onlyOne
                if (name == "id")
                    "_id"
                else
                    name
            case value: StatementEntitySourceValue[_] =>
                "_id"
            case other =>
                unsupported("Mongo storage doesn't support joins.")
        }

    private def toQueryOperator(operator: CompositeOperator): String =
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
                unsupported("Mongo doesn't have the $eq operator yet (https://jira.mongodb.org/browse/SERVER-1367).")
        }

    private def unsupported(msg: String) =
        throw new UnsupportedOperationException(msg)

    private def toQueryStatementBooleanValue(value: StatementBooleanValue) =
        value match {
            case value: Criteria =>
                toQueryCriteria(value)
            case value: SimpleStatementBooleanValue =>
                newList(value.value.toString)
        }

    private def expectedVersion(
        properties: Map[String, StorageValue]) =
        getMongoValue(properties(versionVarName)) match {
            case null =>
                null
            case value: Long =>
                value - 1l
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
                    newList(list.map(elem => getMongoValue(elem).asInstanceOf[Object]): _*)
                }.orNull
            case value: ByteArrayStorageValue =>
                value.value.getOrElse(null)
            case value: ReferenceStorageValue =>
                value.value.getOrElse(null)
        }

    private def newObject(elems: (String, Any)*) =
        Map[String, Any](elems: _*)

    private def newList(elems: Any*) =
        elems.toList

}