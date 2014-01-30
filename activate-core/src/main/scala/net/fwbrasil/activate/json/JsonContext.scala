package net.fwbrasil.activate.json

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.BaseEntity

trait JsonContext[J] {
    val context: ActivateContext

    def createEntityFromJson[E <: BaseEntity: Manifest](json: String): E
    def createEntityFromJson[E <: BaseEntity: Manifest](json: J): E

    def updateEntityFromJson[E <: BaseEntity: Manifest](json: String, id: E#ID): E
    def updateEntityFromJson[E <: BaseEntity: Manifest](json: J, id: E#ID): E
    def updateEntityFromJson[E <: BaseEntity: Manifest](json: String, entity: E): E
    def updateEntityFromJson[E <: BaseEntity: Manifest](json: J, entity: E): E
    def updateEntityFromJson[E <: BaseEntity: Manifest](json: String): E
    def updateEntityFromJson[E <: BaseEntity: Manifest](json: J): E

    def createOrUpdateEntityFromJson[E <: BaseEntity: Manifest](json: String): E
    def createOrUpdateEntityFromJson[E <: BaseEntity: Manifest](json: J): E

    def createJsonStringFromEntity[E <: BaseEntity: Manifest](entity: E, depth: Int = 0, excludeFields: List[String] = List(), includeFields: List[String] = List()): String
    def createJsonFromEntity[E <: BaseEntity: Manifest](entity: E, depth: Int = 0, excludeFields: List[String] = List(), includeFields: List[String] = List()): J

    def fullDepth = Int.MaxValue

    implicit class EntityJsonMethods[E <: BaseEntity: Manifest](val entity: E) {

        def updateFromJson(json: J): E =
            JsonContext.this.updateEntityFromJson(json, entity)

        def updateFromJson(json: String): E =
            JsonContext.this.updateEntityFromJson(json, entity)

        def toJsonString: String = toJsonString()
        def toJsonString(depth: Int = 0, excludeFields: List[String] = List(), includeFields: List[String] = List()): String = 
            createJsonStringFromEntity(entity, depth, excludeFields, includeFields)

        def toJson: J = toJson()
        def toJson(depth: Int = 0, excludeFields: List[String] = List(), includeFields: List[String] = List()): J =
            createJsonFromEntity(entity, depth, excludeFields, includeFields)
    }

}
