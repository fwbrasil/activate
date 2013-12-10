package net.fwbrasil.activate.entity.id

import org.joda.time.DateTime
import java.util.Date
import java.lang.reflect.ParameterizedType
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.EntityHelper
import scala.Array.canBuildFrom
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.ActivateContext
import java.util.concurrent.ConcurrentHashMap
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.ManifestUtil._
import java.lang.reflect.Modifier
import net.fwbrasil.activate.entity.EntityMetadata
import net.fwbrasil.scala.UnsafeLazy._
import scala.reflect.runtime.universe.Type
import net.fwbrasil.smirror._
import scala.util.Try
import java.lang.reflect.InvocationTargetException
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.BaseEntity
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.LongEntityValue
import java.lang.Long
import net.fwbrasil.activate.entity.BooleanEntityValue
import java.lang.Boolean
import net.fwbrasil.activate.entity.CharEntityValue
import net.fwbrasil.activate.entity.StringEntityValue
import net.fwbrasil.activate.entity.FloatEntityValue
import java.lang.Float
import net.fwbrasil.activate.entity.DoubleEntityValue
import java.lang.Double
import net.fwbrasil.activate.entity.BigDecimalEntityValue
import java.math.BigDecimal

trait EntityId {
    this: BaseEntity =>

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

    def idTvalFunctionFor(entityClass: Class[_]) = {
        val idClass = EntityId.idClassFor(entityClass)
        EntityValue.tvalFunction[BaseEntity#ID](idClass, classOf[Object])
    }

    def idToString[ID <: BaseEntity#ID](entityClass: Class[_], id: ID): String =
        id.toString

    def stringToId[ID <: BaseEntity#ID](entityClass: Class[_], idString: String): ID = {
        val entityIdValue = idTvalFunctionFor(entityClass)(None).asInstanceOf[EntityValue[_]]
        val id =
            entityIdValue match {
                case value: IntEntityValue =>
                    Integer.parseInt(idString)
                case value: LongEntityValue =>
                    Long.parseLong(idString)
                case value: BooleanEntityValue =>
                    Boolean.parseBoolean(idString)
                case vale: CharEntityValue =>
                    idString.head
                case value: StringEntityValue =>
                    idString
                case value: FloatEntityValue =>
                    Float.parseFloat(idString)
                case value: DoubleEntityValue =>
                    Double.parseDouble(idString)
                case value: BigDecimalEntityValue =>
                    BigDecimal.valueOf(Double.parseDouble(idString))
            }
        id.asInstanceOf[ID]
    }
}

trait EntityIdContext {
    this: ActivateContext =>

    EntityHelper.initialize(classpathHints)

    type UUID = net.fwbrasil.activate.entity.id.UUID
    type CustomID[ID] = net.fwbrasil.activate.entity.id.CustomID[ID]
    type GeneratedID[ID] = net.fwbrasil.activate.entity.id.GeneratedID[ID]

    protected def reinitializeIdGenerators =
        reload

    private var generatorsByConcreteEntityClass: UnsafeLazyItem[Map[Class[BaseEntity], IdGenerator[BaseEntity]]] = _

    reload

    private def classpathHints = List[Any](this.getClass) ++ entitiesPackages

    protected def entitiesPackages = List[String]()

    private def reload =
        generatorsByConcreteEntityClass =
            unsafeLazy {

                val generatorsByBaseEntityClass =
                    Reflection
                        .getAllImplementorsNames(List(this.getClass, classOf[EntityId]), classOf[IdGenerator[_]])
                        .map(name => ActivateContext.loadClass(name))
                        .filter(clazz => !Modifier.isAbstract(clazz.getModifiers) && !clazz.isInterface)
                        .filter(_.getConstructors.filter(c => !Modifier.isPrivate(c.getModifiers())).nonEmpty)
                        .map { clazz =>
                            (try clazz.newInstance
                            catch {
                                case e: InstantiationException =>
                                    val constructor =
                                        clazz.getConstructors.toList.find { constructor =>
                                            val params = constructor.getParameterTypes
                                            params.size == 1 &&
                                                params.head.isAssignableFrom(this.getClass)
                                        }.getOrElse {
                                            throw new IllegalStateException("Can't instantiate generator " + clazz)
                                        }
                                    constructor.newInstance(this)
                            }).asInstanceOf[IdGenerator[BaseEntity]]
                        }.groupBy(_.entityClass.asInstanceOf[Class[BaseEntity]])
                        .mapValues(_.head)

                val customIdClasses = EntityHelper.allConcreteEntityClasses.toList.filter(!classOf[UUID].isAssignableFrom(_))

                val values =
                    for (entityClass <- customIdClasses) yield {
                        val candidates =
                            generatorsByBaseEntityClass
                                .filterKeys(_.isAssignableFrom(entityClass))
                        candidates.keys.toList
                            .sortWith((c1, c2) => c1.isAssignableFrom(c2)).lastOption.map {
                                mostSpecific =>
                                    entityClass -> candidates(mostSpecific)
                            }
                    }

                values.flatten.toMap
            }

    def nextIdOptionFor[E <: BaseEntity](entityClass: Class[E]) =
        idGeneratorFor(entityClass).map(_.nextId.asInstanceOf[BaseEntity#ID])

    def nextIdFor[E <: BaseEntity](entityClass: Class[E]) =
        nextIdOptionFor(entityClass)
            .getOrElse(throw new IllegalStateException(s"Can't find a id generator for $entityClass."))

    def idGeneratorFor[E <: BaseEntity](entityClass: Class[E]) =
        generatorsByConcreteEntityClass.get.get(entityClass.asInstanceOf[Class[BaseEntity]]).asInstanceOf[Option[IdGenerator[E]]]

}

trait CustomID[T] {
    this: BaseEntity =>

    type ID = T

    val id: T
}

trait GeneratedID[T] extends CustomID[T] {
    this: BaseEntity =>
        
    val id =
        context
            .nextIdOptionFor(getClass)
            .getOrElse(null)
            .asInstanceOf[T]
}
