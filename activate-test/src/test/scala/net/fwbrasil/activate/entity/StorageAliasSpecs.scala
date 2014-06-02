package net.fwbrasil.activate.entity

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.storage.mongo.MongoStorage
import com.mongodb.BasicDBObject
import net.fwbrasil.activate.polyglotContext
import net.fwbrasil.activate.asyncFinagleMysqlContext

@RunWith(classOf[JUnitRunner])
class StorageAliasSpecs extends ActivateTest {

    override def contexts =
        super.contexts.filter(ctx => ctx != polyglotContext && !ctx.storage.isMemoryStorage && !ctx.storage.supportsAsync && ctx != asyncFinagleMysqlContext)

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filter(!_.isInstanceOf[OneTransaction])

    "The @Alias annotation" should {
        "customize entity name" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            new ShortNameEntity("s").id
                        }
                    step {
                        all[ShortNameEntity].head.id mustEqual entityId
                        byId[ShortNameEntity](entityId) must not beEmpty
                    }
                    step {
                        findOnlyOneId("sne") mustEqual entityId
                    }
                })
        }

        "customize entity property name" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        select[ActivateTestEntity]
                            .where(_.customNamedValue :== fullStringValue)
                            .head.id mustEqual entityId
                    }
                    step {
                        findOnlyOneId("ActivateTestEntity", Some("customName", fullStringValue)) mustEqual entityId
                    }
                })
        }
        
        "customize entity property name defined in the constructor" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        select[ActivateTestEntity]
                            .where(_.customNamedConstructorProperty :== fullStringValue)
                            .head.id mustEqual entityId
                    }
                    step {
                        findOnlyOneId("ActivateTestEntity", Some("customNameConst", fullStringValue)) mustEqual entityId
                    }
                })
        }
    }

    private def findOnlyOneId(tableName: String)(implicit context: ActivateTestContext): String =
        findOnlyOneId(tableName, None)

    private def findOnlyOneId(tableName: String, whereOption: Option[(String, String)])(implicit context: ActivateTestContext): String = {
        context.storage match {
            case storage: JdbcRelationalStorage =>
                val where = whereOption.map(tuple => " where " + storage.dialect.escape(tuple._1) + " = '" + tuple._2 + "'").getOrElse("")
                val tableEscaped = storage.dialect.escape(tableName)
                querySqlForString("select id from " + tableEscaped + " " + where, storage)
            case storage: MongoStorage =>
                val mongoDB = storage.directAccess
                mongoDB.collectionExists(tableName) must beTrue
                val coll = mongoDB.getCollection(tableName)
                val query = new BasicDBObject()
                whereOption.map { tuple =>
                    query.put(tuple._1, tuple._2)
                }
                val obj = coll.findOne(query)
                val id = obj.get("_id")
                id.asInstanceOf[String]
        }
    }

    private def querySqlForString(sql: String, storage: JdbcRelationalStorage)(implicit context: ActivateTestContext) = {
        val conn = storage.directAccess
        try {
            val stmt = conn.prepareStatement(sql)
            try {
                val rs = stmt.executeQuery()
                try {
                    rs.next()
                    val string = rs.getString(1)
                    if (rs.next())
                        throw new IllegalStateException("Not a single result query")
                    string
                } finally
                    rs.close
            } finally
                stmt.close
        } finally {
            conn.rollback
            conn.close
        }
    }

}