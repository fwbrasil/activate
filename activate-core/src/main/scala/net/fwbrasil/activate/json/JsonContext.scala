package net.fwbrasil.activate.json

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity

trait JsonContext {
  val context:ActivateContext

  def createEntityFromJson[E <: Entity : Manifest](json: String): E

  def updateEntityFromJson[E <: Entity : Manifest](json: String, entity: E): E

  def createOrUpdateEntityFromJson[E <: Entity : Manifest](json: String): E

  def createJsonFromEntity[E <: Entity : Manifest](entity: E): String

  implicit class EntityJsonMethods[E <: Entity : Manifest](val entity: E) {

    def updateFromJsonString(json: String): E =
      JsonContext.this.updateEntityFromJson(json, entity)

    def toJsonString: String = createJsonFromEntity(entity)
  }

}
