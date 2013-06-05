package net.fwbrasil.activate.jackson.json

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.core.{JsonParser, Version}
import com.fasterxml.jackson.databind._
import net.fwbrasil.activate.entity.{Entity, Var}
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

object ActivateJacksonModule extends SimpleModule("ActivateJacksonModule", new Version(1, 0, 0, null, "hu.finesolution.activate", "entityDeserializer")) {

  class VarDeserializer extends JsonDeserializer[Var[_]] {
    override def deserialize(jp: JsonParser, ctxt: DeserializationContext, intoValue: Var[_]) = null

    def deserialize(jp: JsonParser, ctxt: DeserializationContext) = null
  }

  @JsonIgnoreProperties(Array("_baseVar","_varsMap","_vars","version","dirty","deleted","persisted","validable","initialized","inLiveCache","deletedSnapshot"))
  trait EntityMixin {
  }

  addDeserializer(classOf[Var[_]], new VarDeserializer)
  setMixInAnnotation(classOf[Entity], classOf[EntityMixin])
}
