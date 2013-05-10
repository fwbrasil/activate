package net.fwbrasil.activate.json4s

import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.ActivateContext
import org.json4s.CustomSerializer
import org.json4s.JsonDSL
import org.json4s.Serialization
import org.json4s.JsonAST._
import org.json4s.NoTypeHints
import net.fwbrasil.activate.entity.SerializableEntityValue
import net.fwbrasil.activate.entity.ListEntityValue
import net.fwbrasil.activate.entity.LazyListEntityValue
import net.fwbrasil.activate.entity.EnumerationEntityValue
import net.fwbrasil.activate.entity.DateEntityValue
import net.fwbrasil.activate.entity.FloatEntityValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.CharEntityValue
import net.fwbrasil.activate.entity.DoubleEntityValue
import net.fwbrasil.activate.entity.JodaInstantEntityValue
import net.fwbrasil.activate.entity.LongEntityValue
import net.fwbrasil.activate.entity.StringEntityValue
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.BooleanEntityValue
import net.fwbrasil.activate.entity.ByteArrayEntityValue
import net.fwbrasil.activate.entity.BigDecimalEntityValue
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.CalendarEntityValue
import net.fwbrasil.activate.entity.EntityValue
import java.util.Date
import org.joda.time.Instant
import java.util.Calendar
import net.fwbrasil.activate.entity.IdVar
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.JodaInstantEntityValue
import net.fwbrasil.activate.entity.LazyList
import org.joda.time.base.AbstractInstant
import java.text.SimpleDateFormat
import org.json4s.JsonMethods
import java.text.DateFormat

trait Json4sContext {
    this: ActivateContext =>

    protected val jsonMethods: JsonMethods[_]

    private def toJValue[A](entityValue: EntityValue[A], f: A => JValue): JValue =
        entityValue.value.map(f(_)).getOrElse(JNull)

    protected def jsonDateFormat =
        new SimpleDateFormat("EEE MMM dd HH:mm:ss:SSS zzz yyyy")

    private def fromJValue(value: JValue, entityValue: EntityValue[_]): Any =
        (value, entityValue) match {
            case (JNull, entityValue) =>
                null
            case (value: JInt, entityValue: IntEntityValue) =>
                value.values.intValue
            case (value: JInt, entityValue: LongEntityValue) =>
                value.values.longValue
            case (value: JBool, entityValue: BooleanEntityValue) =>
                value.values
            case (value: JString, entityValue: CharEntityValue) =>
                value.values.charAt(0)
            case (value: JString, entityValue: StringEntityValue) =>
                value.values
            case (value: JDouble, entityValue: FloatEntityValue) =>
                value.values.floatValue
            case (value: JInt, entityValue: FloatEntityValue) =>
                value.values.floatValue
            case (value: JDecimal, entityValue: FloatEntityValue) =>
                value.values.floatValue
            case (value: JInt, entityValue: DoubleEntityValue) =>
                value.values.doubleValue
            case (value: JDouble, entityValue: DoubleEntityValue) =>
                value.values
            case (value: JInt, entityValue: BigDecimalEntityValue) =>
                BigDecimal(value.values)
            case (value: JDouble, entityValue: BigDecimalEntityValue) =>
                BigDecimal(value.values)
            case (value: JDecimal, entityValue: BigDecimalEntityValue) =>
                value.values
            case (value: JString, entityValue: DateEntityValue) =>
                jsonDateFormat.parse(value.values)
            case (value: JString, entityValue: JodaInstantEntityValue[_]) =>
                materializeJodaInstant(entityValue.instantClass, jsonDateFormat.parse(value.values))
            case (value: JString, entityValue: CalendarEntityValue) =>
                val calendar = Calendar.getInstance
                calendar.setTime(jsonDateFormat.parse(value.values))
                calendar
            case (value: JString, entityValue: ByteArrayEntityValue) =>
                value.values.getBytes
            case (value: JString, entityValue: EntityInstanceEntityValue[_]) =>
                byId(value.values).getOrElse(throw new IllegalStateException("Invalid id " + value.values))
            case (value: JString, entityValue: EntityInstanceReferenceValue[_]) =>
                byId(value.values).getOrElse(throw new IllegalStateException("Invalid id " + value.values))
            case (value: JString, entityValue: EnumerationEntityValue[_]) =>
                val enumerationValueClass = entityValue.enumerationClass
                val enumerationClass = enumerationValueClass.getEnclosingClass
                val enumerationObjectClass = Class.forName(enumerationClass.getName + "$")
                val obj = getObject[Enumeration](enumerationObjectClass)
                obj.withName(value.values)
            case (value: JArray, entityValue: ListEntityValue[_]) =>
                value.arr.toList.map(e => fromJValue(e.asInstanceOf[JValue], entityValue.emptyValueEntityValue))
            case (value: JArray, entityValue: LazyListEntityValue[_]) =>
                new LazyList[Entity](value.arr.toList.collect { case JString(id) => id })(entityValue.valueManifest.asInstanceOf[Manifest[Entity]])
            case (value: JString, entityValue: SerializableEntityValue[_]) =>
                entityValue.serializator.fromSerialized(value.values.getBytes)(entityValue.typeManifest)
        }

    private def toJValue(entityValue: EntityValue[_]): JValue =
        entityValue match {
            case value: IntEntityValue =>
                toJValue[Int](value, JInt(_))
            case value: LongEntityValue =>
                toJValue[Long](value, JInt(_))
            case value: BooleanEntityValue =>
                toJValue[Boolean](value, JBool(_))
            case value: CharEntityValue =>
                toJValue[Char](value, c => JString(c.toString))
            case value: StringEntityValue =>
                toJValue[String](value, JString(_))
            case value: FloatEntityValue =>
                toJValue[Float](value, JDouble(_))
            case value: DoubleEntityValue =>
                toJValue[Double](value, JDouble(_))
            case value: BigDecimalEntityValue =>
                toJValue[BigDecimal](value, JDecimal(_))
            case value: DateEntityValue =>
                toJValue[Date](value, d => JString(jsonDateFormat.format(d)))
            case value: JodaInstantEntityValue[AbstractInstant] =>
                toJValue[AbstractInstant](value, d => JString(jsonDateFormat.format(d.toDate)))
            case value: CalendarEntityValue =>
                toJValue[Calendar](value, d => JString(jsonDateFormat.format(d.getTime)))
            case value: ByteArrayEntityValue =>
                toJValue[Array[Byte]](value, b => JString(new String(b)))
            case value: EntityInstanceEntityValue[Entity] =>
                toJValue[Entity](value, e => JString(e.id))
            case value: EntityInstanceReferenceValue[Entity] =>
                toJValue[String](value, JString(_))
            case value: EnumerationEntityValue[Enumeration#Value] =>
                toJValue[Enumeration#Value](value, e => JString(e.toString))
            case value: ListEntityValue[Any] =>
                value.value.map(list =>
                    JArray(list.map(e => toJValue(value.valueEntityValue(e)))))
                    .getOrElse(JNull)
            case value: LazyListEntityValue[_] =>
                value.value.map(list =>
                    JArray(list.ids.map(JString(_))))
                    .getOrElse(JNull)
            case value: SerializableEntityValue[_] =>
                value.value.map(v =>
                    JString(new String(value.serializator.toSerialized(v)(value.typeManifest))))
                    .getOrElse(JNull)
        }

    implicit class EntityJsonMethods[E <: Entity: Manifest](val entity: E) {
        private val jsonMethodsAnyRef = jsonMethods.asInstanceOf[JsonMethods[AnyRef]]
        import jsonMethodsAnyRef._

        def toJson =
            compact(render(toJsonObject(entity)(manifestClass(entity.getClass))))

        def updateFromJson(json: String) =
            updateFromJsonObject(entity, jsonMethods.parse(json).asInstanceOf[JObject])
    }

    private def updateFromJsonObject(entity: Entity, jObject: JObject) = {
        val fields = jObject.obj
        val entityClass = entity.getClass
        val entityMetadata =
            EntityHelper.metadatas.find(_.entityClass == entityClass).get
        val propMetadataMap =
            entityMetadata.propertiesMetadata.mapBy(_.name)
        for ((name, jValue) <- fields if (name != "id")) {
            val property = propMetadataMap(name)
            val ref = entity.varNamed(name)
            val entityValue = ref.toEntityPropertyValue(None)
            val value = fromJValue(jValue, entityValue)
            val propertyMetadata = propMetadataMap(name)
            if (propertyMetadata.isOption)
                ref.put(Option(value))
            else
                ref.putValue(value)
        }
        entity
    }
    
    def createEntityFromJson[E <: Entity: Manifest](json: String): E =
        createEntityFromJson[E](jsonMethods.parse(json).asInstanceOf[JObject])

    def createEntityFromJson[E <: Entity: Manifest](jObject: JObject): E = {
        val entityClass = erasureOf[E]
        val id = IdVar.generateId(entityClass)
        val entity = liveCache.createLazyEntity(entityClass, id)
        entity.setInitialized
        entity.setNotPersisted
        context.liveCache.toCache(entityClass, () => entity)
        updateFromJsonObject(entity, jObject)
        entity
    }
    
    def createOrUpdateEntityFromJson[E <: Entity: Manifest](json: String): E =
        createOrUpdateEntityFromJson[E](jsonMethods.parse(json).asInstanceOf[JObject])

    def createOrUpdateEntityFromJson[E <: Entity: Manifest](jObject: JObject) = {
        jObject.obj.collect {
            case ("id", JString(id)) =>
                val entity = byId[E](id).getOrElse(throw new IllegalStateException("Invalid id " + id))
                updateFromJsonObject(entity, jObject)
                entity
        }.headOption.getOrElse {
            createEntityFromJson[E](jObject)
        }
    }

    private def toJsonObject[E <: Entity: Manifest](entity: E) = {
        val fields = entity.vars.filter(!_.isTransient).map(ref =>
            JField(ref.name, ref.get.map(value => toJValue(ref.toEntityPropertyValue(value))).getOrElse(JNull)))
        JObject(fields)
    }

    class EntityJson4sSerializer[E <: Entity: Manifest] extends CustomSerializer[E](
        format => (
            {
                case jObject: JObject =>
                    createOrUpdateEntityFromJson[E](jObject)
            },
            {
                case entity: E =>
                    toJsonObject[E](entity)
            }))

    def entitySerializers =
        EntityHelper.allConcreteEntityClasses.map(clazz => new EntityJson4sSerializer()(manifestClass(clazz)))


}