package net.fwbrasil.activate.json.jackson

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.core.{ JsonParser, Version }
import com.fasterxml.jackson.databind._
import net.fwbrasil.activate.entity.{ Entity, Var }
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonGenerator

object ActivateJacksonModule extends SimpleModule("ActivateJacksonModule", new Version(1, 0, 0, null, "hu.finesolution.activate", "entityDeserializer")) {

    class VarDeserializer extends JsonDeserializer[Var[_]] {
        override def deserialize(jp: JsonParser, ctxt: DeserializationContext, intoValue: Var[_]) = null

        def deserialize(jp: JsonParser, ctxt: DeserializationContext) = null
    }

    object EnumerationSerializer extends JsonSerializer[Enumeration#Value] {
        def serialize(value: Enumeration#Value, generator: JsonGenerator, provider: SerializerProvider) =
            generator.writeString(value.toString)
    }

    object EnumerationDeserializer extends JsonDeserializer[Enumeration#Value] {
        def deserialize(parser: JsonParser, ctx: DeserializationContext) = {
            null
        }
    }

    @JsonIgnoreProperties(Array("_baseVar", "_varsMap", "_vars", "version", "dirty", "deleted", "persisted", "validable", "initialized", "inLiveCache", "deletedSnapshot"))
    trait EntityMixin {
    }
    addSerializer(classOf[Enumeration#Value], EnumerationSerializer)
    addDeserializer(classOf[Enumeration#Value], EnumerationDeserializer)
    addDeserializer(classOf[Var[_]], new VarDeserializer)
    setMixInAnnotation(classOf[Entity], classOf[EntityMixin])
}
