package net.fwbrasil.activate.json

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity

trait JsonContext[J] {
    val context: ActivateContext

    def createEntityFromJson[E <: Entity: Manifest](json: String): E
    def createEntityFromJson[E <: Entity: Manifest](json: J): E

    def updateEntityFromJson[E <: Entity: Manifest](json: String, id: String): E
    def updateEntityFromJson[E <: Entity: Manifest](json: J, id: String): E
    def updateEntityFromJson[E <: Entity: Manifest](json: String, entity: E): E
    def updateEntityFromJson[E <: Entity: Manifest](json: J, entity: E): E
    def updateEntityFromJson[E <: Entity: Manifest](json: String): E
    def updateEntityFromJson[E <: Entity: Manifest](json: J): E

    def createOrUpdateEntityFromJson[E <: Entity: Manifest](json: String): E
    def createOrUpdateEntityFromJson[E <: Entity: Manifest](json: J): E

    def createJsonStringFromEntity[E <: Entity: Manifest](entity: E, depth: Int = 0): String
    def createJsonFromEntity[E <: Entity: Manifest](entity: E, depth: Int = 0): J
    
    def fullDepth = Int.MaxValue

    implicit class EntityJsonMethods[E <: Entity: Manifest](val entity: E) {

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
