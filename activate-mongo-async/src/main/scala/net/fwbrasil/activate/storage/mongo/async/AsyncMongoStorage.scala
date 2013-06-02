package net.fwbrasil.activate.storage.mongo.async

import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import reactivemongo.api.MongoDriver
import reactivemongo.api.MongoConnection
import reactivemongo.api.DefaultDB
import net.fwbrasil.activate.storage.mongo.BaseDBObject
import reactivemongo.bson.BSONDocument
import reactivemongo.api._
import reactivemongo.bson._
import java.util.Date
import net.fwbrasil.activate.storage.mongo.BaseDBList

class AsyncMongoDBObject(var doc: BSONDocument) extends BaseDBObject {
    def put(key: String, obj: Any) =
        doc ++= (obj match {
            case v: Int =>
                BSONDocument(key -> v)
            case v: Long =>
                BSONDocument(key -> v)
            case v: Boolean =>
                BSONDocument(key -> v)
            case v: String =>
                BSONDocument(key -> v)
            case v: Date =>
                BSONDocument(key -> v.getTime)
            case v: Double =>
                BSONDocument(key -> v)
            case v: Array[Byte] =>
                BSONDocument(key -> new String(v))
            case v: AsyncMongoDBObject =>
                v.doc
        })
    def get(key: String) =
        doc.get(key).orNull
}

class AsyncMongoDBList(var list: BSONArray) extends BaseDBList {
    def add(obj: Object) =
        obj.asInstanceOf[Any] match {
            case v: Int =>
                list ++= BSONArray(v)
            case v: Long =>
                list ++= BSONArray(v)
            case v: Boolean =>
                list ++= BSONArray(v)
            case v: String =>
                list ++= BSONArray(v)
            case v: Date =>
                list ++= BSONArray(v.getTime)
            case v: Double =>
                list ++= BSONArray(v)
            case v: Array[Byte] =>
                list ++= BSONArray(new String(v))
            case v: AsyncMongoDBObject =>
                v.doc
    }
    def toList =
        list.values.toList
}

trait AsyncMongoStorage extends MarshalStorage[DefaultDB] {

    val host: String
    val port: Int = 27017
    val db: String
    val authentication: Option[(String, String)] = None

    def isMemoryStorage = false
    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = false

    implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

    private val driver = new MongoDriver

    lazy val mongoDB = (new MongoDriver).connection(List(host))(db)

    def directAccess = mongoDB

}