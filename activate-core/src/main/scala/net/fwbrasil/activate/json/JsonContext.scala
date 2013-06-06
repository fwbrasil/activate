package net.fwbrasil.activate.json

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity

trait JsonContext {

  def createEntityFromJson[E <: Entity : Manifest](json: String)(implicit context: ActivateContext): E

  def updateEntityFromJson[E <: Entity : Manifest](json: String, entity: E)(implicit context: ActivateContext): E

  def createOrUpdateEntityFromJson[E <: Entity : Manifest](json: String)(implicit context: ActivateContext): E

  def createJsonFromEntity[E <: Entity : Manifest](entity: E)(implicit context: ActivateContext): String

  implicit class EntityJsonMethods[E <: Entity : Manifest](val entity: E) {

    def entityFromJson(json: String)(implicit context: ActivateContext): E =
      updateEntityFromJson(json, entity)

    def entityToJson(implicit context: ActivateContext): String = createJsonFromEntity(entity)
  }

}
