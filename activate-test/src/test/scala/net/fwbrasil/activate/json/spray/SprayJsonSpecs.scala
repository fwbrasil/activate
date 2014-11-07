package net.fwbrasil.activate.json.spray

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.memoryContext
import net.fwbrasil.activate.util.RichList._
import spray.json._
import DefaultJsonProtocol._
import net.fwbrasil.activate.entity.BaseEntity
import org.joda.time.DateTime
import net.fwbrasil.activate.asyncPostgresqlContext
import net.fwbrasil.activate.postgresqlContext
import net.fwbrasil.activate.polyglotContext
import net.fwbrasil.activate.mysqlContext
import net.fwbrasil.activate.h2Context
import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.async.AsyncPostgreSQLStorage
import net.fwbrasil.activate.entity.id.UUID
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.storage.relational.async.AsyncSQLStorage

class Event(
    val name: String,
    val description: String,
    val subdomain: String,
    val internalId: String,
    val singleTerms: List[String],
    val compoundTerms: List[String],
    var boundingBoxes: List[BoundingBox]) extends Entity

class BoundingBox(val swCorner: GeoData, val neCorner: GeoData) extends Entity

case class GeoData(latitude: Float, longitude: Float) extends Entity

@RunWith(classOf[JUnitRunner])
class SprayJsonSpecs extends ActivateTest {

    "The SprayJson support" should {
        "manipulate json strings" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    object SprayJsonContext extends SprayJsonContext {
                        val context = step.ctx
                    }
                    import SprayJsonContext._

                    val (emptyEntityId, fullEntityId) =
                        step {
                            (newEmptyActivateTestEntity.id, newFullActivateTestEntity.id)
                        }
                    def emptyEntity = byId[ActivateTestEntity](emptyEntityId).get
                    def fullEntity = byId[ActivateTestEntity](fullEntityId).get
                    val (emptyEntityJson, fullEntityJson) =
                        step {
                            (emptyEntity.toJson, fullEntity.toJson)
                        }
                    step {
                        emptyEntity.toJsonString === emptyEntityJson.compactPrint
                        fullEntity.toJsonString === fullEntityJson.compactPrint
                    }
                    step {
                        emptyEntity.updateFromJson(fullEntityJson.compactPrint)
                        fullEntity.updateFromJson(emptyEntityJson)
                    }
                    step {
                        validateFullTestEntity(emptyEntity)
                        validateEmptyTestEntity(fullEntity)
                    }
                    step {
                        updateEntityFromJson(fullEntityJson.compactPrint, fullEntity)
                        updateEntityFromJson(emptyEntityJson, emptyEntity)
                    }
                    step {
                        validateFullTestEntity(fullEntity)
                        validateEmptyTestEntity(emptyEntity)
                    }
                    step {
                        updateEntityFromJson[ActivateTestEntity](fullEntityJson.compactPrint, emptyEntity.id)
                        updateEntityFromJson[ActivateTestEntity](emptyEntityJson, fullEntity.id)
                    }
                    step {
                        validateFullTestEntity(emptyEntity)
                        validateEmptyTestEntity(fullEntity)
                    }
                    step {
                        updateEntityFromJson[ActivateTestEntity](fullEntityJson.compactPrint)
                        updateEntityFromJson[ActivateTestEntity](emptyEntityJson)
                    }
                    step {
                        validateFullTestEntity(fullEntity)
                        validateEmptyTestEntity(emptyEntity)
                    }
                    val (newFullEntityId1, newEmptyEntityId1) =
                        step {
                            createOrUpdateEntityFromJson[ActivateTestEntity](fullEntityJson)
                            createOrUpdateEntityFromJson[ActivateTestEntity](emptyEntityJson.compactPrint)
                            def removeId(json: JsValue) =
                                JsObject(json.asJsObject.fields - "id")
                            (createOrUpdateEntityFromJson[ActivateTestEntity](removeId(fullEntityJson)).id,
                                removeId(emptyEntityJson).convertTo[ActivateTestEntity].id)
                        }
                    step {
                        validateFullTestEntity(fullEntity)
                        validateEmptyTestEntity(emptyEntity)
                        validateEmptyTestEntity(byId[ActivateTestEntity](newEmptyEntityId1).get)
                        validateFullTestEntity(byId[ActivateTestEntity](newFullEntityId1).get)
                    }
                    val (newFullEntityId2, newEmptyEntityId2) =
                        step {
                            (createEntityFromJson[ActivateTestEntity](fullEntityJson.compactPrint).id,
                                createEntityFromJson[ActivateTestEntity](emptyEntityJson).id)
                        }
                    step {
                        validateEmptyTestEntity(byId[ActivateTestEntity](newEmptyEntityId2).get)
                        validateFullTestEntity(byId[ActivateTestEntity](newFullEntityId2).get)
                    }
                })
        }

        "support entity lists" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    object SprayJsonContext extends SprayJsonContext {
                        val context = step.ctx
                    }
                    import SprayJsonContext._
                    step {
                        val entity =
                            createEntityFromJson[Event]("""
                                        {
        								    "name": "Foo",
        								    "singleTerms": [
        								        "Foo",
        								        "Bar"
        								    ],
        								    "compoundTerms": [
        								        "foo bar"
        								    ],
        								    "boundingBoxes": [
        								        {
        								            "swCorner": {
        								                "latitude": 30.2,
        								                "longitude": -81.75
        								            },
        								            "neCorner": {
        								                "latitude": 30.37,
        								                "longitude": -81.45
        								            }
        								        }
        								    ]
        								}
                                        """)

                        entity.name === "Foo"
                        entity.singleTerms === List("Foo", "Bar")
                        entity.compoundTerms === List("foo bar")
                        val box = entity.boundingBoxes.onlyOne
                        box.swCorner.latitude === 30.2f
                        box.swCorner.longitude === -81.75f
                        box.neCorner.latitude === 30.37f
                        box.neCorner.longitude === -81.45f
                    }
                })
        }

        "support depth print" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    object SprayJsonContext extends SprayJsonContext {
                        val context = step.ctx
                    }
                    import SprayJsonContext._
                    val entityId =
                        step {
                            createEntityFromJson[Event]("""
                                        {
        								    "name": "Foo",
        								    "singleTerms": [
        								        "Foo",
        								        "Bar"
        								    ],
        								    "compoundTerms": [
        								        "foo bar"
        								    ],
        								    "boundingBoxes": [
        								        {
        								            "swCorner": {
        								                "latitude": 30.2,
        								                "longitude": -81.75
        								            },
        								            "neCorner": {
        								                "latitude": 30.37,
        								                "longitude": -81.45
        								            }
        								        }
        								    ]
        								}
                                        """).id
                        }
                    def entity = byId[Event](entityId).get
                    step {
                        val depth0 =
                            s"""{"name":"Foo","singleTerms":["Foo","Bar"],"description":null,"subdomain":null,"id":"${entity.id}","boundingBoxes":["${entity.boundingBoxes.onlyOne.id}"],"compoundTerms":["foo bar"],"internalId":null}"""
                        val depth1 =
                            s"""{"name":"Foo","singleTerms":["Foo","Bar"],"description":null,"subdomain":null,"id":"${entity.id}","boundingBoxes":[{"neCorner":"${entity.boundingBoxes.onlyOne.neCorner.id}","swCorner":"${entity.boundingBoxes.onlyOne.swCorner.id}","id":"${entity.boundingBoxes.onlyOne.id}"}],"compoundTerms":["foo bar"],"internalId":null}"""
                        val depth2 =
                            s"""{"name":"Foo","singleTerms":["Foo","Bar"],"description":null,"subdomain":null,"id":"${entity.id}","boundingBoxes":[{"neCorner":{"latitude":30.3700008392334,"id":"${entity.boundingBoxes.onlyOne.neCorner.id}","longitude":-81.44999694824219},"swCorner":{"latitude":30.200000762939453,"id":"${entity.boundingBoxes.onlyOne.swCorner.id}","longitude":-81.75},"id":"${entity.boundingBoxes.onlyOne.id}"}],"compoundTerms":["foo bar"],"internalId":null}"""
                        entity.toJsonString == depth0
                        entity.toJsonString(depth = 0) === depth0
                        entity.toJsonString(depth = 1) === depth1
                        entity.toJsonString(depth = 2) === depth2
                        entity.toJsonString(fullDepth) === depth2
                    }
                })
        }

        "handle cycle in depth print" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (!step.ctx.storage.isInstanceOf[JdbcRelationalStorage] &&
                        !step.ctx.storage.isInstanceOf[AsyncSQLStorage[_]]) {
                        object SprayJsonContext extends SprayJsonContext {
                            val context = step.ctx
                        }
                        import SprayJsonContext._
                        val (id1, id2) =
                            step {
                                val employee1 = new Employee("test1", None)
                                val employee2 = new Employee("test2", None)
                                (employee1.id, employee2.id)
                            }
                        def employee1 = byId[Employee](id1).get
                        def employee2 = byId[Employee](id2).get
                        step {
                            employee1.supervisor = Some(employee2)
                            employee2.supervisor = Some(employee1)
                        }
                        step {
                            val depth0 = s"""{"name":"test1","id":"$id1","supervisor":"$id2"}"""
                            val depth1 = s"""{"name":"test1","id":"$id1","supervisor":{"name":"test2","id":"$id2","supervisor":"$id1"}}"""
                            employee1.toJsonString === depth0
                            employee1.toJsonString(depth = 0) === depth0
                            employee1.toJsonString(depth = 1) === depth1
                            employee1.toJsonString(depth = 2) === depth1
                            employee1.toJsonString(fullDepth) === depth1
                        }
                    }
                })
        }
        "filter fields to render" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        object SprayJsonContext extends SprayJsonContext {
                            val context = step.ctx
                        }
                        import SprayJsonContext._
                        val entity = new Supplier("name", "city")
                        entity.toJson(excludeFields = List("id", "name")).compactPrint === """{"city":"city"}"""
                        entity.toJsonString(includeFields = List("name")) === """{"name":"name"}"""
                        entity.toJsonString(excludeFields = List("id", "name"), includeFields = List("city")) === """{"city":"city"}"""
                    }
                })
        }
        "support custom json fild name" in {
            "serialization" in
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            object SprayJsonContext extends SprayJsonContext {
                                val context = step.ctx
                            }
                            import SprayJsonContext._
                            val entity = new SimpleEntity(222, 1)
                            entity.toJsonString(includeFields = List("jsonIntValue")) === """{"jsonIntValue":1}"""
                            entity.toJsonString(excludeFields = List("id")) === """{"jsonIntValue":1}"""
                        }
                    })
            "deserialization" in
                activateTest(
                    (step: StepExecutor) => {
                        import step.ctx._
                        step {
                            object SprayJsonContext extends SprayJsonContext {
                                val context = step.ctx
                            }
                            import SprayJsonContext._
                            val entity = createEntityFromJson[SimpleEntity]("""{"id": 444, "jsonIntValue":1}""")
                            entity.id === 444
                            entity.intValue = 1
                        }
                    })
        }
    }

}