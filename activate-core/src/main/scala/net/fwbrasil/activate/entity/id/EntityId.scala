package net.fwbrasil.activate.entity.id

import org.joda.time.DateTime
import java.util.Date
import java.lang.reflect.ParameterizedType
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import scala.Array.canBuildFrom
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.ActivateContext
import java.util.concurrent.ConcurrentHashMap

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

trait EntityIdContext {
    this: ActivateContext =>

    type UUID = net.fwbrasil.activate.entity.id.UUID
    type CustomID[ID] = net.fwbrasil.activate.entity.id.CustomID[ID]

    val idGenerators = Map[Class[_], IdGenerator[_]]()

    def nextIdFor[E <: EntityId](entityClass: Class[E]) =
        idGeneratorFor(entityClass).nextId(entityClass).asInstanceOf[Entity#ID]

    import scala.collection.JavaConversions._

    private val idGeneratorsCache = new ConcurrentHashMap[Class[_], IdGenerator[_]]

    def idGeneratorFor[E <: EntityId](entityClass: Class[E]) =
        idGeneratorsCache.getOrElse(entityClass, {
            if (classOf[UUID].isAssignableFrom(entityClass))
                UUIDGenerator
            else {
                val candidates = idGenerators.filterKeys(_.isAssignableFrom(entityClass))
                ???
            }
        })

}

trait IdGenerator[ID] {
    def nextId(entityClass: Class[_]): ID
}

trait CustomID[T] {
    this: Entity =>

    type ID = T

    val id: T
}