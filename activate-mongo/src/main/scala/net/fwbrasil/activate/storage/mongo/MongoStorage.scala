package net.fwbrasil.activate.storage.mongo

import net.fwbrasil.activate.storage.marshalling._
import com.mongodb.Mongo
import com.mongodb.DB
import com.mongodb.DBCollection
import com.mongodb.BasicDBObject
import com.mongodb.BasicDBList
import com.mongodb.DBObject
import com.mongodb.DBCursor
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper.getEntityName
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.Reflection.toNiceObject
import scala.collection.JavaConversions._
import java.util.Date
import net.fwbrasil.activate.entity._
import java.util.regex.Pattern
import net.fwbrasil.activate.migration.AddReference
import net.fwbrasil.activate.migration.RemoveReference
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.CompositeOperator
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import scala.collection.mutable.ListBuffer
import java.util.IdentityHashMap
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.serialization.javaSerializator

trait MongoStorage extends MarshalStorage[DB] {

	val host: String
	val port: Int = 27017
	val db: String
	val authentication: Option[(String, String)] = None

	def directAccess =
		mongoDB

	lazy val mongoDB = {
		val conn = new Mongo(host, port)
		val ret = conn.getDB(db)
		if (authentication.isDefined) {
			val (user, password) = authentication.get
			ret.authenticate(user, password.toArray[Char])
		}
		ret
	}

	override def supportComplexQueries = false
	override def hasStaticScheme = false

	override def store(
		statements: List[MassModificationStatement],
		insertList: List[(Entity, Map[String, StorageValue])],
		updateList: List[(Entity, Map[String, StorageValue])],
		deleteList: List[(Entity, Map[String, StorageValue])]): Unit = {

		storeStatements(statements)
		storeInserts(insertList)
		storeUpdates(updateList)
		storeDeletes(deleteList)

	}

	private def storeDeletes(deleteList: List[(Entity, Map[String, StorageValue])]) =
		for ((entity, properties) <- deleteList) {
			val query = new BasicDBObject()
			query.put("_id", entity.id)
			coll(entity).remove(query)
		}

	private def storeUpdates(updateList: List[(Entity, Map[String, StorageValue])]) =
		for ((entity, properties) <- updateList) {
			val query = new BasicDBObject
			query.put("_id", entity.id)
			val set = new BasicDBObject
			for ((name, value) <- properties if (name != "id")) {
				val inner = new BasicDBObject
				set.put(name, getMongoValue(value))
			}
			val update = new BasicDBObject
			update.put("$set", set)
			coll(entity).update(query, update)
		}

	private def storeInserts(insertList: List[(Entity, Map[String, StorageValue])]) = {
		val insertMap = new IdentityHashMap[Class[_], ListBuffer[BasicDBObject]]()
		for ((entity, properties) <- insertList) {
			val doc = new BasicDBObject()
			for ((name, value) <- properties if (name != "id"))
				doc.put(name, getMongoValue(value))
			doc.put("_id", entity.id)
			insertMap.getOrElseUpdate(entity.getClass, ListBuffer()) += doc
		}
		for (entityClass <- insertMap.keys)
			coll(entityClass).insert(insertMap(entityClass))
	}

	private def storeStatements(statements: List[MassModificationStatement]) =
		for (statement <- statements) {
			val (coll, where) = collectionAndWhere(statement.from, statement.where)
			statement match {
				case update: MassUpdateStatement =>
					val set = new BasicDBObject
					for (assignment <- update.assignments)
						set.put(mongoStatementSelectValue(assignment.assignee), getMongoValue(assignment.value))
					val mongoUpdate = new BasicDBObject
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
				javaSerializator.toSerialized(value.value.getOrElse(null))
			case value: ByteArrayStorageValue =>
				value.value.getOrElse(null)
			case value: ReferenceStorageValue =>
				value.value.getOrElse(null)
		}

	private[this] def coll(entity: Entity): DBCollection =
		coll(entity.niceClass)

	private[this] def coll(entityClass: Class[_]): DBCollection =
		coll(getEntityName(entityClass))

	private[this] def coll(entityName: String): DBCollection =
		mongoDB.getCollection(entityName)

	def query(queryInstance: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
		val from = queryInstance.from
		val (coll, where) = collectionAndWhere(from, queryInstance.where)
		val selectValues = queryInstance.select.values //query(queryInstance.select.values: _*)
		val select = new BasicDBObject
		for (value <- selectValues)
			if (!value.isInstanceOf[SimpleValue[_]])
				select.put(mongoStatementSelectValue(value), 1)
		val entitySource = queryInstance.from.entitySources.onlyOne
		val ret = coll.find(where, select)
		val rows = ret.toArray
		(for (row <- rows) yield (for (i <- 0 until selectValues.size) yield {
			selectValues(i) match {
				case value: SimpleValue[_] =>
					expectedTypes(i)
				case other =>
					getValue(row, mongoStatementSelectValue(other), expectedTypes(i))
			}
		}).toList).toList
	}

	def getValue(obj: DBObject, name: String, storageValue: StorageValue): StorageValue =
		storageValue match {
			case value: IntStorageValue =>
				IntStorageValue(getValue[Int](obj, name))
			case value: LongStorageValue =>
				LongStorageValue(getValue[Long](obj, name))
			case value: BooleanStorageValue =>
				BooleanStorageValue(getValue[Boolean](obj, name))
			case value: StringStorageValue =>
				StringStorageValue(getValue[String](obj, name))
			case value: FloatStorageValue =>
				FloatStorageValue(getValue[Double](obj, name).map(_.floatValue))
			case value: DateStorageValue =>
				DateStorageValue(getValue[Date](obj, name))
			case value: DoubleStorageValue =>
				DoubleStorageValue(getValue[Double](obj, name))
			case value: BigDecimalStorageValue =>
				BigDecimalStorageValue(getValue[Double](obj, name).map(BigDecimal(_)))
			case value: ListStorageValue =>
				ListStorageValue(getValue[Array[Byte]](obj, name).map(javaSerializator.fromSerialized[List[Any]]), value.clazz)
			case value: ByteArrayStorageValue =>
				ByteArrayStorageValue(getValue[Array[Byte]](obj, name))
			case value: ReferenceStorageValue =>
				ReferenceStorageValue(getValue[String](obj, name))
		}

	def getValue[T](obj: DBObject, name: String) =
		Option(obj.get(name).asInstanceOf[T])

	def query(values: StatementSelectValue[_]*): Seq[String] =
		for (value <- values)
			yield mongoStatementSelectValue(value)

	def mongoStatementSelectValue(value: StatementSelectValue[_]) =
		value match {
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

	def query(criteria: Criteria): DBObject = {
		val obj = new BasicDBObject
		criteria match {
			case criteria: BooleanOperatorCriteria =>
				val list = new BasicDBList
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
					val innerObj = new BasicDBObject
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
						val temp = new BasicDBObject
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
			case other =>
				throw new UnsupportedOperationException("Mongo storage accept only simple values in the left side of a criteria (no joins).")
		}

	def query(value: StatementBooleanValue): DBObject =
		value match {
			case value: Criteria =>
				query(value)
			case value: SimpleStatementBooleanValue =>
				val list = new BasicDBList
				list.add(value.value.toString)
				list
		}

	def queryEntityProperty(value: StatementValue): String =
		value match {
			case value: StatementEntitySourcePropertyValue[_] =>
				val name = value.propertyPathNames.onlyOne
				if (name == "id")
					"_id"
				else
					name
			case value: StatementEntitySourceValue[_] =>
				"_id"
			case other =>
				throw new UnsupportedOperationException("Mongo storage supports only entity properties on the left side of a criteria.")
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
			case operator: IsEqualTo =>
				throw new UnsupportedOperationException("Mongo doesn't have $eq operator yet (https://jira.mongodb.org/browse/SERVER-1367).")
		}

	override def migrateStorage(action: ModifyStorageAction): Unit =
		action match {
			case action: StorageCreateTable =>
				if (!action.ifNotExists || !mongoDB.collectionExists(action.tableName))
					mongoDB.createCollection(action.tableName, new BasicDBObject)
			case action: StorageRenameTable =>
				coll(action.oldName).rename(action.newName)
			case action: StorageRemoveTable =>
				coll(action.name).drop
			case action: StorageAddColumn =>
			// Do nothing!
			case action: StorageRenameColumn =>
				val update = new BasicDBObject
				val updateInner = new BasicDBObject
				updateInner.put(action.oldName, action.column.name)
				update.put("$rename", updateInner)
				coll(action.tableName).update(new BasicDBObject, update)
			case action: StorageRemoveColumn =>
				val update = new BasicDBObject
				val updateInner = new BasicDBObject
				updateInner.put(action.name, 1)
				update.put("$unset", updateInner)
				coll(action.tableName).update(new BasicDBObject, update)
			case action: StorageAddIndex =>
				val obj = new BasicDBObject
				obj.put(action.columnName, 1)
				coll(action.tableName).ensureIndex(obj)
			case action: StorageRemoveIndex =>
				val obj = new BasicDBObject
				obj.put(action.columnName, 1)
				coll(action.tableName).dropIndex(obj)
			case action: StorageAddReference =>
			// Do nothing!
			case action: StorageRemoveReference =>
			// Do nothing!
		}

	private def collectionAndWhere(from: From, where: Where) = {
		val mongoWhere = query(where.value)
		val entitySource = from.entitySources.onlyOne("Mongo storage supports only simple queries (only one 'from' entity and without nested properties)")
		val mongoCollection = coll(entitySource.entityClass)
		(mongoCollection, mongoWhere)
	}

}

object MongoStorageFactory extends StorageFactory {
	override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] = {
		new MongoStorage {
			override val host = properties("host")
			override val port = Integer.parseInt(properties("port"))
			override val db = properties("db")
			override val authentication =
				properties.get("user").map(user => (user, properties("password")))
		}
	}
}
