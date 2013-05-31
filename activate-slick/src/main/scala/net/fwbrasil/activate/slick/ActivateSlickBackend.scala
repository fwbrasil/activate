package net.fwbrasil.activate.slick

import scala.reflect.runtime.universe.Mirror
import scala.reflect.runtime.universe.Type
import scala.reflect.runtime.universe.TypeRef
import scala.reflect.runtime.universe.TypeTag
import scala.reflect.runtime.universe.TypeRefApi
import scala.slick.ast.FieldSymbol
import scala.slick.ast.Node
import scala.slick.ast.NullaryNode
import scala.slick.ast.ProductNode
import scala.slick.ast.Select
import scala.slick.ast.TableNode
import scala.slick.ast.WithOp
import scala.slick.direct.SlickBackend
import scala.slick.driver.BasicDriver
import scala.slick.lifted.Shape
import scala.slick.lifted.ShapedValue
import scala.slick.session.PositionedResult
import scala.slick.session.Session

import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.Marshaller
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom
import net.fwbrasil.smirror.sClassOf

class ActivateSlickBackend(driver: BasicDriver, dialect: SqlIdiom, val mapper: ActivateEntityMapper)(implicit val mirror: Mirror, val ctx: ActivateContext)
        extends SlickBackend(driver, mapper) {

    override protected def resultByType(expectedType: Type, rs: PositionedResult, session: Session): Any = {
        ActivateSlickBackend.entityMetadataOption(expectedType).map {
            metadata =>
                val id = rs.nextString
                ctx.byId[Entity](id).get
        }.getOrElse {
            try {
                super.resultByType(expectedType, rs, session)
            } catch {
                case e: MatchError =>
                    val typeArguments =
                        expectedType match {
                            case sig: TypeRefApi =>
                                sig.args.map(sClassOf[Any](_).javaClassOption.get)
                            case other =>
                                List()
                        }
                    val tval = EntityValue.tvalFunction(sClassOf(expectedType).javaClassOption.get, typeArguments.headOption.getOrElse(classOf[Object]))
                    val emptyEntityValue = tval(None)
                    val emptyStorageValue = Marshaller.marshalling(emptyEntityValue)
                    val storageValue =
                        emptyStorageValue match {
                            case value: IntStorageValue =>
                                IntStorageValue(rs.nextIntOption)
                            case value: LongStorageValue =>
                                LongStorageValue(rs.nextLongOption)
                            case value: BooleanStorageValue =>
                                BooleanStorageValue(rs.nextBooleanOption)
                            case value: StringStorageValue =>
                                StringStorageValue(rs.nextStringOption)
                            case value: FloatStorageValue =>
                                FloatStorageValue(rs.nextFloatOption)
                            case value: DoubleStorageValue =>
                                DoubleStorageValue(rs.nextDoubleOption)
                            case value: BigDecimalStorageValue =>
                                BigDecimalStorageValue(rs.nextBigDecimalOption)
                            case value: DateStorageValue =>
                                DateStorageValue(rs.nextDateOption)
                            case value: ByteArrayStorageValue =>
                                ByteArrayStorageValue(rs.nextBytesOption)
                            case value: ListStorageValue =>
                                throw new UnsupportedOperationException("Slick query does not support lists.")
                        }
                    val entityValue =
                        Marshaller.unmarshalling(storageValue, emptyEntityValue)
                    entityValue.value.getOrElse(null)
            }

        }
    }

    override def getConstructorArgs(tpe: Type) = {
        val a = ActivateSlickBackend.entityMetadataOption(tpe).map {
            metadata =>
                val entitySClass = sClassOf(metadata.entityClass)
                metadata.propertiesMetadata.map(p => entitySClass.fields.find(_.name == p.originalName).get.symbol)
        }.getOrElse {
            super.getConstructorArgs(tpe)
        }
        a
    }

    override def typetagToQuery(typetag: TypeTag[_]): Query = {
        val table = new TableNode with NullaryNode with WithOp {
            lazy val tableName = mapper.typeToTable(typetag.tpe)
            def columns =
                ActivateSlickBackend.entityMetadataOption(typetag.tpe).map {
                    metadata =>
                        List(Select(Node(this), FieldSymbol("id")(List(), null)))
                }.getOrElse {
                    getConstructorArgs(typetag.tpe).map { extractColumn(_, Node(this)) } // use def here, not val, so expansion is still correct after cloning
                }

            def nodeShaped_* = ShapedValue(ProductNode(columns), Shape.selfLinearizingShape.asInstanceOf[Shape[ProductNode, Any, _]])
        }
        new Query(table, Scope())
    }

}

object ActivateSlickBackend {
    def entityMetadataOption(tpe: Type)(implicit mirror: Mirror) =
        tpe match {
            case TypeRef(_, sym, _) =>
                if (sym.isClass)
                    sClassOf[Entity](sym.asClass.typeSignature).javaClassOption.flatMap(c => EntityHelper.getEntityMetadataOption(c))
                else
                    EntityHelper.metadatas.find(_.name == sym.name.toString)
            case _ =>
                EntityHelper.metadatas.find(_.name == tpe.termSymbol.name.toString)

        }
}

