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

    def createJsonStringFromEntity[E <: BaseEntity: Manifest](entity: E, depth: Int = 0): String
    def createJsonFromEntity[E <: BaseEntity: Manifest](entity: E, depth: Int = 0): J
    
    def fullDepth = Int.MaxValue

    implicit class EntityJsonMethods[E <: BaseEntity: Manifest](val entity: E) {

        def updateFromJson(json: J): E =
            JsonContext.this.updateEntityFromJson(json, entity)

        def updateFromJson(json: String): E =
            JsonContext.this.updateEntityFromJson(json, entity)

        def toJsonString: String = toJsonString(0)
        def toJsonString(depth: Int): String = createJsonStringFromEntity(entity, depth)
        
        def toJson: J = toJson(0)
        def toJson(depth: Int): J = createJsonFromEntity(entity, depth)
    }

}
