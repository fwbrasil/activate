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
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.ManifestUtil._
import java.lang.reflect.Modifier
import net.fwbrasil.activate.entity.EntityMetadata
import scala.reflect.runtime.universe.Type
import net.fwbrasil.smirror._

trait EntityId {
    this: Entity =>

    type ID

    val id: ID
}

object EntityId {

    def idClassFor(entityClass: Class[_]): Class[_] =
        if (classOf[UUID].isAssignableFrom(entityClass))
            classOf[String]
        else if (classOf[CustomID[_]].isAssignableFrom(entityClass)) {
            implicit val mirror = runtimeMirror(entityClass.getClassLoader)
            val fields = sClassOf(entityClass).fields
            val getterSymbol = fields.find(_.name == "id").get.getterSymbol
            val typ = Reflection.get(Reflection.get(getterSymbol, "mtpeResult"), "resultType").asInstanceOf[Type]
            val sClass = sClassOf(typ)
            val clazz = sClass.javaClassOption.get
            clazz
        } else
            throw new IllegalStateException("Invalid id type for " + entityClass)
}

trait EntityIdContext {
    this: ActivateContext =>

    EntityHelper.initialize(this.getClass)

    type UUID = net.fwbrasil.activate.entity.id.UUID
    type CustomID[ID] = net.fwbrasil.activate.entity.id.CustomID[ID]

    private val generatorsByConcreteEntityClass = {

        val generatorsByBaseEntityClass =
            Reflection
                .getAllImplementorsNames(List(this.getClass, classOf[EntityId]), classOf[IdGenerator[_]])
                .map(name => ActivateContext.loadClass(name))
                .filter(clazz => !Modifier.isAbstract(clazz.getModifiers) && !clazz.isInterface)
                .map(Reflection.getObjectOption(_).asInstanceOf[Option[IdGenerator[Entity]]])
                .flatten.groupBy(_.entityClass.asInstanceOf[Class[Entity]])
                .mapValues(_.head)

        val values =
            for (entityClass <- EntityHelper.allConcreteEntityClasses.toList) yield {
                if(entityClass.getName.endsWith("ValidationEntity"))
                    println(1)
                val candidates =
                    generatorsByBaseEntityClass
                        .filterKeys(_.isAssignableFrom(entityClass))
                val mostSpecific =
                    candidates.keys.toList
                        .sortWith((c1, c2) => c1.isAssignableFrom(c2)).lastOption.getOrElse {
                            throw new IllegalStateException("Can't find a generator for entity class " + entityClass)
                        }
                entityClass -> candidates(mostSpecific)
            }

        values.toMap
    }

    def nextIdFor[E <: Entity](entityClass: Class[E]) =
        idGeneratorFor(entityClass).nextId(entityClass).asInstanceOf[Entity#ID]

    def idGeneratorFor[E <: Entity](entityClass: Class[E]): IdGenerator[E] =
        generatorsByConcreteEntityClass(entityClass.asInstanceOf[Class[Entity]])
            .asInstanceOf[IdGenerator[E]]

}

trait CustomID[T] {
    this: Entity =>

    type ID = T

    val id = null.asInstanceOf[T]
}