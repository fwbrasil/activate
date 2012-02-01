package net.fwbrasil.activate.storage.mongo

import net.fwbrasil.activate.storage.marshalling._
import com.mongodb.Mongo;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.query.Query
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper.getEntityName
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.util.Reflection.toNiceObject
import scala.collection.JavaConversions._
import net.fwbrasil.activate.query._
import java.util.Date
import net.fwbrasil.activate.entity._

trait MongoStorage extends MarshalStorage {

	val host: String
	val port: Int = 27017
	val db: String
	val authentication: Option[(String, String)] = None
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

	def store(insertMap: Map[Entity, Map[String, StorageValue]], updateMap: Map[Entity, Map[String, StorageValue]], deleteMap: Map[Entity, Map[String, StorageValue]]): Unit = {
		for ((entity, properties) <- insertMap) {
			val doc = new BasicDBObject();
			for ((name, value) <- properties; if (name != "id"))
				doc.put(name, getMongoValue(value))
			doc.put("_id", entity.id)
			coll(entity).insert(doc)
			val ret = coll(entity).find()
		}
		for ((entity, properties) <- updateMap) {
			val query = new BasicDBObject();
			query.put("_id", entity.id)
			val update = new BasicDBObject();
			for ((name, value) <- properties; if (name != "id"))
				update.put(name, getMongoValue(value))
			coll(entity).update(query, update)
		}
		for ((entity, properties) <- deleteMap) {
			val query = new BasicDBObject();
			query.put("_id", entity.id)
			coll(entity).remove(query)
		}
	}

	def getMongoValue(value: StorageValue): Any =
		value match {
			case value: IntStorageValue =>
				value.value.map(_.intValue).getOrElse(null)
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
			case value: ByteArrayStorageValue =>
				value.value.getOrElse(null)
			case value: ReferenceStorageValue =>
				value.value.getOrElse(null)
		}

	private[this] def coll(entity: Entity): DBCollection =
		coll(entity.niceClass)

	private[this] def coll(entityClass: Class[_]): DBCollection =
		mongoDB.getCollection(getEntityName(entityClass))

	def query(queryInstance: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
		if (queryInstance.from.entitySources.size != 1)
			throw new UnsupportedOperationException("Mongo storage supports only simple queries (only one 'from' entity and without nested properties)")
		val entitySource = queryInstance.from.entitySources.onlyOne
		val where = query(queryInstance.where.value)
		val selectValues = query(queryInstance.select.values: _*)
		val select = new BasicDBObject
		for (value <- selectValues)
			select.put(value, 1)
		val ret = coll(entitySource.entityClass).find(where, select)
		val rows = ret.toArray
		(for (row <- rows) yield (for (i <- 0 until selectValues.size)
			yield getValue(row, selectValues(i), expectedTypes(i))).toList).toList
	}

	def getValue(obj: DBObject, name: String, storageValue: StorageValue): StorageValue =
		storageValue match {
			case value: IntStorageValue =>
				IntStorageValue(getValue[Int](obj, name))(value.entityValue)
			case value: BooleanStorageValue =>
				BooleanStorageValue(getValue[Boolean](obj, name))(value.entityValue)
			case value: StringStorageValue =>
				StringStorageValue(getValue[String](obj, name))(value.entityValue)
			case value: FloatStorageValue =>
				FloatStorageValue(getValue[Double](obj, name).map(_.floatValue))(value.entityValue)
			case value: DateStorageValue =>
				DateStorageValue(getValue[Date](obj, name))(value.entityValue)
			case value: DoubleStorageValue =>
				DoubleStorageValue(getValue[Double](obj, name))(value.entityValue)
			case value: BigDecimalStorageValue =>
				BigDecimalStorageValue(getValue[Double](obj, name).map(BigDecimal(_)))(value.entityValue)
			case value: ByteArrayStorageValue =>
				ByteArrayStorageValue(getValue[Array[Byte]](obj, name))(value.entityValue)
			case value: ReferenceStorageValue =>
				ReferenceStorageValue(getValue[String](obj, name))(value.entityValue)
		}

	def getValue[T](obj: DBObject, name: String) =
		Option(obj.get(name).asInstanceOf[T])

	def query(values: QuerySelectValue[_]*): Seq[String] =
		for (value <- values)
			yield value match {
			case value: QueryEntitySourcePropertyValue[_] =>
				value.propertyPathNames.onlyOne
			case value: QueryEntitySourceValue[_] =>
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
				val value = query(criteria.valueB)
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
					case value: IsNone =>
						null
					case value: IsSome =>
						val temp = new BasicDBObject
						temp.put("$ne", null)
						temp
				}
				obj.put(property, value)
				obj
		}
	}

	def query(value: QueryValue): Any =
		value match {
			case value: SimpleQueryBooleanValue =>
				getMongoValue(Marshaller.marshalling(value.value))
			case value: SimpleValue[_] =>
				getMongoValue(Marshaller.marshalling(value.entityValue))
			case value: QueryEntityInstanceValue[_] =>
				getMongoValue(StringStorageValue(Option(value.entityId))(EntityInstanceEntityValue(None)))
			case other =>
				throw new UnsupportedOperationException("Mongo storage accept only simple values in the left side of a criteria.")
		}

	def query(value: QueryBooleanValue): DBObject =
		value match {
			case value: Criteria =>
				query(value)
			case value: SimpleQueryBooleanValue =>
				val list = new BasicDBList
				list.add(value.value.toString)
				list
		}

	def queryEntityProperty(value: QueryValue): String =
		value match {
			case value: QueryEntitySourcePropertyValue[_] =>
				value.propertyPathNames.onlyOne
			case value: QueryEntitySourceValue[_] =>
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
			case operator: IsEqualTo =>
				throw new UnsupportedOperationException("Mongo doesn't have $eq operator yet (https://jira.mongodb.org/browse/SERVER-1367).")
		}

}
