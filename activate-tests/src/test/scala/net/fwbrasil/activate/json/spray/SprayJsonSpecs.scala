package net.fwbrasil.activate.json.spray

import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.memoryContext
import spray.json._
import DefaultJsonProtocol._
import net.fwbrasil.activate.entity.Entity
import org.joda.time.DateTime

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
                        emptyEntity.updateFromJsonString(fullEntityJson.compactPrint)
                        fullEntity.updateFromJsonString(emptyEntityJson.compactPrint)
                    }
                    step {
                        validateFullTestEntity(emptyEntity)
                        validateEmptyTestEntity(fullEntity)
                    }
                    val (newFullEntityId1, newEmptyEntityId1) =
                        step {
                            createOrUpdateEntityFromJson[ActivateTestEntity](fullEntityJson)
                            createOrUpdateEntityFromJson[ActivateTestEntity](emptyEntityJson)
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
                            (createEntityFromJson[ActivateTestEntity](fullEntityJson).id,
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

                        println(entity)
                    }
                })
        }
    }

}