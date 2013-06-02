package net.fwbrasil.activate.storage.mongo

import scala.collection.JavaConversions._
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
//import net.fwbrasil.activate.util.RichList._
import com.mongodb.MongoClient
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.statement.query.OrderedQuery
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.statement.ToLowerCase
import com.mongodb.WriteResult

class MongoDBObject(val obj: DBObject) extends BaseDBObject {
    def put(key: String, value: Any) =
        obj.put(key,
            value match {
                case list: MongoDBList =>
                    list.list
                case obj: MongoDBObject =>
                    obj.obj
                case other =>
                    other
            })
    def get(key: String) =
        obj.get(key) match {
            case list: BasicDBList =>
                new MongoDBList(list)
            case obj: DBObject =>
                new MongoDBObject(obj)
            case other =>
                other
        }
}

class MongoDBList(val list: BasicDBList) extends MongoDBObject(list) with BaseDBList {
    def add(obj: Object) =
        list.add(
            obj match {
                case list: MongoDBList =>
                    list.list
                case obj: MongoDBObject =>
                    obj.obj
                case other =>
                    other
            })
    def toList =
        list.toList
}

class MongoDBCursor(val result: DBCursor) extends BaseDBCursor[MongoDBObject] {
    def limit(elems: Int) = result.limit(elems)
    def sort(obj: MongoDBObject) = result.sort(obj.obj)
    def close = result.close
    def count = result.count
    def toArray = result.toArray.toList.map(o => new MongoDBObject(o))
}

class MongoDBWriteResult(val result: WriteResult) extends BaseDBWriteResult {
    def getN = result.getN
}

case class MongoDBColl(coll: DBCollection)
        extends BaseDBColl[MongoDBObject, MongoDBCursor, MongoDBWriteResult] {
    def find(query: MongoDBObject) =
        new MongoDBCursor(coll.find(query.obj))
    def find(query: MongoDBObject, select: MongoDBObject) =
        new MongoDBCursor(coll.find(query.obj, select.obj))
    def remove(query: MongoDBObject) =
        new MongoDBWriteResult(coll.remove(query.obj))
    def update(query: MongoDBObject, update: MongoDBObject) =
        new MongoDBWriteResult(coll.update(query.obj, update.obj))
    def updateMulti(query: MongoDBObject, update: MongoDBObject) =
        new MongoDBWriteResult(coll.updateMulti(query.obj, update.obj))
    def insert(list: List[MongoDBObject]) =
        new MongoDBWriteResult(coll.insert(list.map(_.obj)))
    def drop =
        coll.drop
    def dropIndex(obj: MongoDBObject) =
        coll.dropIndex(obj.obj)
    def rename(newName: String) =
        coll.rename(newName)
    def ensureIndex(obj: MongoDBObject, options: MongoDBObject) =
        coll.ensureIndex(obj.obj, options.obj)
}

trait MongoStorage
        extends BaseMongoDriver[DB, MongoDBObject, MongoDBList, MongoDBCursor, MongoDBWriteResult, MongoDBColl] with DelayedInit {

    def directAccess =
        mongoDB

    private var mongoDB: DB = _

    override def delayedInit(body: => Unit) = {
        body
        val conn = new MongoClient(host, port)
        mongoDB = conn.getDB(db)
        if (authentication.isDefined) {
            val (user, password) = authentication.get
            mongoDB.authenticate(user, password.toArray[Char])
        }
        mongoDB
    }

    protected def newDBObject =
        new MongoDBObject(new BasicDBObject)
    protected def newDBList =
        new MongoDBList(new BasicDBList)

    protected def coll(name: String) =
        new MongoDBColl(mongoDB.getCollection(name))
    protected def collectionExists(name: String) =
        mongoDB.collectionExists(name)
    protected def createCollection(name: String, obj: MongoDBObject) =
        mongoDB.createCollection(name, obj.obj)
    protected def collHasIndex(name: String, column: String) =
        mongoDB.getCollection(name).getIndexInfo().find(_.containsField(name)).nonEmpty

}

//object MongoStorageFactory extends StorageFactory {
//    class MongoStorageFromFactory(properties: Map[String, String]) extends MongoStorage {
//        override val host = properties("host")
//        override val port = Integer.parseInt(properties("port"))
//        override val db = properties("db")
//        override val authentication =
//            properties.get("user").map(user => (user, properties("password")))
//    }
//    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
//        new MongoStorageFromFactory(properties)
//}
