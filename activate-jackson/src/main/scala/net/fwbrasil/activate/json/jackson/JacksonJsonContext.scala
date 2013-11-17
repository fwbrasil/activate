//package net.fwbrasil.activate.json.jackson
//
//import net.fwbrasil.activate.ActivateContext
//import com.fasterxml.jackson.databind.{JsonNode, DeserializationFeature, SerializationFeature, ObjectMapper}
//import java.io.StringWriter
//import net.fwbrasil.activate.entity.Entity
//import net.fwbrasil.activate.json.JsonContext
//
//trait JacksonJsonContext extends JsonContext[String] {
//
//  def mapper = new ObjectMapper {
//    registerModule(ActivateScalaModule)
//    registerModule(ActivateJacksonModule)
//    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
//    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//    configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
//  }
//
//  def parse[T](value: String)(implicit m: scala.Predef.Manifest[T]): T = {
//    mapper.reader(m.runtimeClass.asInstanceOf[Class[T]]).readValue(value.getBytes)
//  }
//
//  def parse[T](value: String, obj: T)(implicit m: scala.Predef.Manifest[T]): T = {
//    mapper.reader(m.runtimeClass.asInstanceOf[Class[T]]).withValueToUpdate(obj).readValue(value.getBytes())
//  }
//
//
//  def json(value: Any): String = {
//    val writer = new StringWriter
//    mapper.writeValue(writer, value)
//    writer.toString
//  }
//
//  def createEntityFromJson[E <: Entity : Manifest](json: String): E = {
//    parse[E](json)
//  }
//
//  def updateEntityFromJson[E <: Entity : Manifest](json: String, entity: E): E = {
//    parse[E](json, entity)
//  }
//
//  def createJsonFromEntity[E <: Entity : Manifest](entity: E) = {
//    json(entity)
//  }
//
//  def createOrUpdateEntityFromJson[E <: Entity : Manifest](json: String): E = {
//    mapper.readTree(json).get("id") match {
//      case id: JsonNode =>
//        val entity = context.byId[E](id.asText()).get
//        parse[E](json, entity)
//      case _ => parse[E](json)
//    }
//
//  }
//}
