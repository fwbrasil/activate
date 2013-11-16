package net.fwbrasil.activate.play

import play.api.data.FormError
import play.api.data.Form
import play.api.data.Mapping
import play.api._
import play.api.mvc._
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import play.api.data.validation.Constraint
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.entity.IdVar
import play.api.data.format.Formatter
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityMetadata
import scala.annotation.implicitNotFound
import net.fwbrasil.radon.transaction.TransactionalExecutionContext
import scala.concurrent.Future
import net.fwbrasil.activate.entity.map.MutableEntityMap
import net.fwbrasil.activate.entity.map.EntityMapBase

@implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
class EntityForm[T <: Entity](
    mapping: EntityMapping[T],
    data: Map[String, String] = Map.empty,
    errors: Seq[FormError] = Nil,
    value: Option[MutableEntityMap[T]] = None)(
        implicit context: ActivateContext,
        m: Manifest[T])
        extends Form[MutableEntityMap[T]](mapping, data, errors, value) {

    implicit val entityMetadata = EntityHelper.getEntityMetadata(erasureOf[T])

    def fillWith(entityMap: EntityMapBase[T, _]) =
        new EntityForm[T](mapping, mapping.unbind(new MutableEntityMap(entityMap.values))._1)
    
    def fillWith(entity: T): EntityForm[T] =
        fillWith(new MutableEntityMap(entity))
}

object EntityForm {

    import language.implicitConversions

    implicit def entityMap[T <: Entity](entity: T)(implicit context: ActivateContext, m: Manifest[T]): MutableEntityMap[T] =
        new MutableEntityMap(entity)

    def entity[T <: Entity](implicit context: ActivateContext, m: Manifest[T]) =
        of[T](new EntityFormatter[T])

    def apply[T <: Entity](
        mappings: ((T) => (_, Mapping[_]))*)(
            implicit context: ActivateContext,
            m: Manifest[T]): EntityForm[T] =
        build(mappings.toList)

    private def build[T <: Entity](mappings: List[(T) => (_, Mapping[_])])(implicit context: ActivateContext, m: Manifest[T]): EntityForm[T] = {
        val mock = StatementMocks.mockEntity(erasureOf[T])
        val map = mappings.map(_(mock)).toList.asInstanceOf[List[(Any, Mapping[Any])]]
        val stack = StatementMocks.fakeVarCalledStack.reverse.distinct
        require(stack.size == map.size)
        val properties =
            for (i <- 0 until map.size) yield {
                val property = stack(i).name
                val mapping = map(i)._2
                map(i)._2.withPrefix(property)
            }
        implicit val entityMetadata = EntityHelper.getEntityMetadata(erasureOf[T])
        val mapping = new EntityMapping(properties.toList)
        new EntityForm(mapping)
    }
}

case class EntityMapping[T <: Entity](
    val properties: List[Mapping[_]],
    val key: String = "",
    val constraints: Seq[Constraint[MutableEntityMap[T]]] = Nil)(
        implicit context: ActivateContext,
        m: Manifest[T],
        entityMetadata: EntityMetadata)
        extends Mapping[MutableEntityMap[T]]
        with ObjectMapping {

    def bind(data: Map[String, String]) = {
        val results = properties.map(e => (e.key, e.bind(data)))
        merge(results.map(_._2): _*) match {
            case Left(errors) => Left(errors)
            case Right(values) => {
                require(values.size == results.size)
                val data =
                    for (i <- 0 until results.size)
                        yield (results(i)._1, values(i))
                applyConstraints(new MutableEntityMap(data.toMap))
            }
        }
    }

    def unbind(value: MutableEntityMap[T]) = {
        val data = value.values
        val res =
            (for ((key, value) <- data)
                yield properties.find(_.key == key).map(_.asInstanceOf[Mapping[Any]].unbind(value))).flatten
        res.foldLeft(
            (Map[String, String](), Seq[FormError]()))(
                (t1, t2) => (t1._1 ++ t2._1, t1._2 ++ t2._2))
    }

    def withPrefix(prefix: String) =
        addPrefix(prefix).map(newKey => this.copy(key = newKey)).getOrElse(this)

    def verifying(addConstraints: Constraint[MutableEntityMap[T]]*) = {
        this.copy(constraints = constraints ++ addConstraints.toSeq)
    }

    val mappings = Seq(this) ++ properties

}

class EntityFormatter[T <: Entity](
    implicit context: ActivateContext,
    m: Manifest[T])
        extends Formatter[T] {

    override val format = Some("format.entity", Nil)

    def bind(key: String, data: Map[String, String]) = {
        stringFormat.bind(key, data).right.flatMap { s =>
            scala.util.control.Exception.allCatch[T]
                .either(
                    context.byId[T](s).get)
                .left.map(e => Seq(FormError(key, "error.entity", Seq(s))))
        }
    }

    def unbind(key: String, value: T) = Map(key -> value.id.toString)
}