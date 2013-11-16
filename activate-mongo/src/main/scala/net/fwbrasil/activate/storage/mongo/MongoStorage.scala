package net.fwbrasil.activate.storage.mongo

import java.util.Date
import java.util.IdentityHashMap
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import com.mongodb.BasicDBList
import com.mongodb.BasicDBObject
import com.mongodb.DB
import com.mongodb.DBCollection
import com.mongodb.DBCursor
import com.mongodb.DBObject
import com.mongodb.Mongo
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper.getEntityName
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.statement.CompositeOperator
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.util.RichList._
import com.mongodb.MongoClient
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.statement.query.OrderedQuery
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.statement.ToLowerCase
import scala.collection.mutable.{ Map => MutableMap }
import language.implicitConversions
import com.mongodb.MongoClientOptions
import com.mongodb.ServerAddress

trait MongoStorage extends MarshalStorage[DB] with DelayedInit {

    val host: String
    val port: Int = 27017
    val db: String
    val authentication: Option[(String, String)] = None

    val poolSize = 20

    def directAccess =
        mongoDB

    private var mongoDB: DB = _

    override def delayedInit(body: => Unit) = {
        body
        val options = MongoClientOptions.builder.connectionsPerHost(poolSize).build
        val address = new ServerAddress(host, port)
        val conn = new MongoClient(address, options)
        mongoDB = conn.getDB(db)
        if (authentication.isDefined) {
            val (user, password) = authentication.get
            mongoDB.authenticate(user, password.toArray[Char])
        }
        mongoDB
    }

    def isMemoryStorage = false
    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = false

    override def store(
        readList: List[(Entity, Long)],
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        preVerifyStaleData(
            readList ++
                mongoIdiom.expectedVersions(updateList) ++
                mongoIdiom.expectedVersions(deleteList))
        storeStatements(statements)
        storeInserts(insertList)
        storeUpdates(updateList)
        storeDeletes(deleteList)

        None
    }

    private def dbValue(obj: Any): Any =
        obj match {
            case map: Map[_, _] =>
                dbObject(map.asInstanceOf[Map[String, Any]])
            case list: List[Any] =>
                dbList(list)
            case other =>
                other
        }

    private def dbList(list: List[Any]) = {
        val dbList = new BasicDBList
        for (v <- list)
            dbList.add(dbValue(v).asInstanceOf[Object])
        dbList
    }

    private def dbObject(map: Map[String, Any]) = {
        val obj = new BasicDBObject
        for ((key, value) <- map)
            obj.put(key, dbValue(value))
        obj
    }

    private def preVerifyStaleData(
        data: List[(Entity, Long)]) = {
        val queries = mongoIdiom.findStaleDataQueries(data)
        val stale =
            (for ((entity, query, select) <- queries) yield {
                coll(entity).find(dbObject(query), dbObject(select)).toArray.toList
                    .map(_.get("_id").asInstanceOf[Entity#ID]).map(id => (id, entity.niceClass))
            }).flatten
        if (stale.nonEmpty)
            staleDataException(stale.toSet)
    }

    private def storeDeletes(deleteList: List[(Entity, Map[String, StorageValue])]) =
        for ((entity, properties) <- deleteList) {
            val query = mongoIdiom.toDelete(entity, properties)
            val result = coll(entity).remove(dbObject(query))
            if (result.getN != 1)
                staleDataException(Set((entity.id, entity.niceClass)))
        }

    private def storeUpdates(updateList: List[(Entity, Map[String, StorageValue])]) =
        for ((entity, properties) <- updateList) {
            val (query, set) = mongoIdiom.toUpdate(entity, properties)
            val result = coll(entity).update(dbObject(query), dbObject(set))
            if (result.getN != 1)
                staleDataException(Set((entity.id, entity.niceClass)))
        }

    private def storeInserts(insertList: List[(Entity, Map[String, StorageValue])]) = {
        val insertMap = mongoIdiom.toInsertMap(insertList)
        for (entityClass <- insertMap.keys)
            coll(entityClass).insert(
                insertMap(entityClass).map(dbObject(_)))
    }

    private def storeStatements(statements: List[MassModificationStatement]) =
        for (statement <- statements) {
            val where = dbObject(mongoIdiom.toQueryWhere(statement.where))
            val coll = this.coll(statement.from)
            statement match {
                case update: MassUpdateStatement =>
                    val mongoUpdate = mongoIdiom.toQueryUpdate(update)
                    coll.updateMulti(where, dbObject(mongoUpdate))
                case delete: MassDeleteStatement =>
                    coll.remove(where)
            }
        }

    private[this] def coll(from: From): DBCollection =
        coll(mongoIdiom.collectionClass(from))

    private[this] def coll(entity: Entity): DBCollection =
        coll(entity.getClass)

    private[this] def coll(entityClass: Class[_]): DBCollection =
        coll(getEntityName(entityClass))

    private[this] def coll(entityName: String): DBCollection =
        mongoDB.getCollection(entityName)

    override def query(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] = {

        val (where, select) = mongoIdiom.toQuery(query, entitiesReadFromCache)
        val ret = coll(query.from).find(dbObject(where), dbObject(select))

        val order = mongoIdiom.toQueryOrder(query)
        if (order.nonEmpty)
            ret.sort(dbObject(order))

        limitQueryIfNecessary(query, ret)

        val result =
            ret.toArray.toList

        mongoIdiom.transformResultToTheExpectedTypes[DBObject](
            expectedTypes,
            query.select.values,
            result,
            rowToColumn = (doc, name) => doc.get(name),
            fromDBList = obj => obj.asInstanceOf[BasicDBList].toList)

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
                val update = mongoIdiom.renameColumn(action.oldName, action.column.name)
                coll(action.tableName).update(new BasicDBObject, dbObject(update))
            case action: StorageRemoveColumn =>
                val update = mongoIdiom.removeColumn(action.name)
                coll(action.tableName).update(new BasicDBObject, dbObject(update))
            case action: StorageAddIndex =>
                val obj = new BasicDBObject
                obj.put(action.columnName, 1)
                val options = new BasicDBObject
                if (action.unique)
                    options.put("unique", true)
                if (!action.ifNotExists || !collHasIndex(action.tableName, action.columnName))
                    coll(action.tableName).ensureIndex(obj, options)
            case action: StorageRemoveIndex =>
                val obj = new BasicDBObject
                obj.put(action.columnName, 1)
                if (!action.ifExists || collHasIndex(action.tableName, action.columnName))
                    coll(action.tableName).dropIndex(obj)
            case action: StorageModifyColumnType =>
            // Do nothing!
            case action: StorageAddReference =>
            // Do nothing!
            case action: StorageRemoveReference =>
            // Do nothing!
            case action: StorageCreateListTable =>
            // Do nothing!
            case action: StorageRemoveListTable =>
            // Do nothing!
        }

    private def collHasIndex(name: String, column: String) =
        coll(name).getIndexInfo().find(_.containsField(name)).nonEmpty

    private def limitQueryIfNecessary(query: Query[_], ret: DBCursor) =
        query match {
            case q: LimitedOrderedQuery[_] =>
                ret.limit(q.limit)
                q.offsetOption.map(ret.skip)
            case other =>
        }

}

object MongoStorageFactory extends StorageFactory {
    class MongoStorageFromFactory(properties: Map[String, String]) extends MongoStorage {
        override val host = properties("host")
        override val port = Integer.parseInt(properties("port"))
        override val db = properties("db")
        override val authentication =
            properties.get("user").map(user => (user, properties("password")))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new MongoStorageFromFactory(properties)
}
