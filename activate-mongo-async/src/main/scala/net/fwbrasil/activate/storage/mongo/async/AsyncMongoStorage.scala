package net.fwbrasil.activate.storage.mongo.async

import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import reactivemongo.api.MongoDriver
import reactivemongo.api.MongoConnection
import reactivemongo.api.DefaultDB

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