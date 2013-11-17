package net.fwbrasil.activate.entity

import org.joda.time.DateTime
import java.util.Date
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.ActivateContext
import java.lang.reflect.ParameterizedType

trait EntityId {
    this: Entity =>

    type ID <: AnyRef

    val id: ID
}

object EntityId {

    def idClassFor(entityClass: Class[_]): Class[_] =
        if (classOf[UUID].isAssignableFrom(entityClass))
            classOf[String]
        else if (classOf[CustomID[_]].isAssignableFrom(entityClass)) {
            val interfaces =
                entityClass.getGenericInterfaces.collect {
                    case typ: ParameterizedType =>
                        typ
                }
            println(interfaces)
            ???
        } else
            throw new IllegalStateException("Invalid id type for " + entityClass)
}

trait UUID {
    this: Entity =>

    type ID = String

    final val id: String = null

    def creationTimestamp = UUIDUtil timestamp id.substring(0, 35)
    def creationDate = new Date(creationTimestamp)
    def creationDateTime = new DateTime(creationTimestamp)
}

trait CustomID[T] {
    this: Entity =>

    type ID = T

    val id: T
}