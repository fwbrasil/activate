//package net.fwbrasil.activate.slick
//
//import net.fwbrasil.activate.util.ManifestUtil._
//import language.implicitConversions
//import net.fwbrasil.smirror._
//import net.fwbrasil.activate.ActivateContext
//import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
//import net.fwbrasil.activate.storage.memory.TransientMemoryStorage
//import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
//import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
//import scala.slick.direct.Queryable
//import scala.slick.direct.SlickBackend
//import scala.slick.direct.Mapper
//import scala.slick.driver.PostgresDriver
//import scala.slick.session.Session
//import scala.slick.session.Database
//import javax.sql.DataSource
//import java.sql.Connection
//import scala.slick.direct.AnnotationMapper._
//import net.fwbrasil.activate.entity.EntityHelper
//import net.fwbrasil.activate.entity.EntityMetadata
//import scala.reflect.runtime.universe.Mirror
//import scala.slick.driver.BasicDriver
//import scala.slick.session.PositionedResult
//import reflect.runtime.universe.Type
//import scala.slick.direct.AnnotationMapper
//import net.fwbrasil.activate.entity.EntityValue
//import scala.reflect.runtime.universe.TypeRefApi
//import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom
//import net.fwbrasil.activate.storage.marshalling.StorageValue
//import net.fwbrasil.activate.storage.marshalling.Marshaller
//import net.fwbrasil.activate.storage.marshalling.IntStorageValue
//import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
//import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
//import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
//import net.fwbrasil.activate.storage.marshalling.LongStorageValue
//import net.fwbrasil.activate.storage.marshalling.StringStorageValue
//import net.fwbrasil.activate.storage.marshalling.DateStorageValue
//import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
//import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
//import net.fwbrasil.activate.storage.marshalling.ListStorageValue
//import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
//import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
//import scala.slick.lifted.ShapedValue
//import scala.slick.lifted.Shape
//import scala.reflect.runtime.universe._
//import scala.slick.ast.ProductNode
//import scala.slick.ast.TableNode
//import scala.slick.ast.NullaryNode
//import scala.slick.ast.Node
//import scala.slick.ast.WithOp
//import scala.slick.ast.Select
//import net.fwbrasil.activate.entity.Entity
//import scala.slick.ast.FieldSymbol
//import java.util.Date
//import net.fwbrasil.activate.statement.StatementMocks
//import scala.collection.mutable.{ Map => MutableMap }
//
//object ctx extends ActivateContext with SlickQueryContext {
//    val storage = new PooledJdbcRelationalStorage {
//        val jdbcDriver = "org.postgresql.Driver"
//        val user = "postgres"
//        val password = "postgres"
//        val url = "jdbc:postgresql://127.0.0.1/activate_test"
//        val dialect = postgresqlDialect
//    }
//}
//
//class ActivateEntityMapper(implicit val mirror: Mirror) extends Mapper {
//    private def metadataOption(tpe: reflect.runtime.universe.Type): Option[EntityMetadata] =
//        ActivateSlickBackend.entityMetadataOption(tpe)
//    def fieldToColumn(sym: reflect.runtime.universe.Symbol): String = {
//        val ownerClass =
//            if (sym.owner.isClass)
//                sym.owner.typeSignature
//            else
//                sym.owner.owner.typeSignature
//        metadataOption(ownerClass).get
//            .propertiesMetadata.find(_.originalName == sym.name.toString).get.name
//    }
//    def isMapped(tpe: reflect.runtime.universe.Type): Boolean =
//        metadataOption(tpe).isDefined
//    def typeToTable(tpe: reflect.runtime.universe.Type): String =
//        metadataOption(tpe).get.name
//}
//
//class ActivateSlickBackend(driver: BasicDriver, dialect: SqlIdiom, val mapper: ActivateEntityMapper)(implicit val mirror: Mirror, val ctx: ActivateContext)
//        extends SlickBackend(driver, mapper) {
//
//    override protected def resultByType(expectedType: Type, rs: PositionedResult, session: Session): Any = {
//        ActivateSlickBackend.entityMetadataOption(expectedType).map {
//            metadata =>
//                val id = rs.nextString
//                ctx.byId(id)
//        }.getOrElse {
//            try {
//                super.resultByType(expectedType, rs, session)
//            } catch {
//                case e: MatchError =>
//                    val typeArguments =
//                        expectedType match {
//                            case sig: TypeRefApi =>
//                                sig.args.map(sClassOf[Any](_).javaClassOption.get)
//                            case other =>
//                                List()
//                        }
//                    val tval = EntityValue.tvalFunction(sClassOf(expectedType).javaClassOption.get, typeArguments.headOption.getOrElse(classOf[Object]))
//                    val emptyEntityValue = tval(None)
//                    val emptyStorageValue = Marshaller.marshalling(emptyEntityValue)
//                    val storageValue =
//                        emptyStorageValue match {
//                            case value: IntStorageValue =>
//                                IntStorageValue(rs.nextIntOption)
//                            case value: LongStorageValue =>
//                                LongStorageValue(rs.nextLongOption)
//                            case value: BooleanStorageValue =>
//                                BooleanStorageValue(rs.nextBooleanOption)
//                            case value: StringStorageValue =>
//                                StringStorageValue(rs.nextStringOption)
//                            case value: FloatStorageValue =>
//                                FloatStorageValue(rs.nextFloatOption)
//                            case value: DoubleStorageValue =>
//                                DoubleStorageValue(rs.nextDoubleOption)
//                            case value: BigDecimalStorageValue =>
//                                BigDecimalStorageValue(rs.nextBigDecimalOption)
//                            case value: DateStorageValue =>
//                                DateStorageValue(rs.nextDateOption)
//                            case value: ByteArrayStorageValue =>
//                                ByteArrayStorageValue(rs.nextBytesOption)
//                            case value: ListStorageValue =>
//                                throw new UnsupportedOperationException("Slick query does not support lists.")
//                        }
//                    val entityValue =
//                        Marshaller.unmarshalling(storageValue, emptyEntityValue)
//                    entityValue.value.getOrElse(null)
//            }
//
//        }
//    }
//
//    override def getConstructorArgs(tpe: Type) = {
//        val a = ActivateSlickBackend.entityMetadataOption(tpe).map {
//            metadata =>
//                val entitySClass = sClassOf(metadata.entityClass)
//                metadata.propertiesMetadata.map(p => entitySClass.fields.find(_.name == p.originalName).get.symbol)
//        }.getOrElse {
//            super.getConstructorArgs(tpe)
//        }
//        a
//    }
//
//    override def typetagToQuery(typetag: TypeTag[_]): Query = {
//        val table = new TableNode with NullaryNode with WithOp {
//            val tableName = mapper.typeToTable(typetag.tpe)
//            def columns =
//                ActivateSlickBackend.entityMetadataOption(typetag.tpe).map {
//                    metadata =>
//                        List(Select(Node(this), FieldSymbol("id")(List(), null)))
//                }.getOrElse {
//                    getConstructorArgs(typetag.tpe).map { extractColumn(_, Node(this)) } // use def here, not val, so expansion is still correct after cloning
//                }
//
//            def nodeShaped_* = ShapedValue(ProductNode(columns), Shape.selfLinearizingShape.asInstanceOf[Shape[ProductNode, Any, _]])
//        }
//        new Query(table, Scope())
//    }
//
//}
//
//object ActivateSlickBackend {
//    def entityMetadataOption(tpe: Type)(implicit mirror: Mirror) =
//        sClassOf[Entity](toRealType(tpe)).javaClassOption.flatMap(c => EntityHelper.getEntityMetadataOption(c))
//
//    def toRealType(tpe: Type) =
//        tpe match {
//            case TypeRef(_, sym, _) =>
//                sym.asClass.typeSignature
//            case _ =>
//                tpe
//        }
//}
//
//trait SlickQueryContext {
//    this: ActivateContext =>
//
//    val storage: JdbcRelationalStorage
//    val driver = PostgresDriver
//
//    import driver.simple._
//
//        implicit class QueryableToSeq[T](queryable: Queryable[T]) {
//            def toSeq =
//                database.withSession {
//                    session: Session =>
//                        backend.result(queryable, session).toSeq
//                }
//        }
//
//    implicit val mirror = scala.reflect.runtime.currentMirror
//
//    lazy val backend = new ActivateSlickBackend(driver, storage.asInstanceOf[JdbcRelationalStorage].dialect, new ActivateEntityMapper)
//
//    lazy val database =
//        new Database {
//            override def createConnection = storage.directAccess.asInstanceOf[Connection]
//        }
//
//    class EntitySlickTable[E <: Entity: Manifest] extends Table[E](EntityHelper.getEntityName(erasureOf[E])) {
//        val entityClass = erasureOf[E]
//        val entityMock = StatementMocks.mockEntityWithoutCache[E](entityClass)
//        tablesThreadLocal.get.put(entityMock, this)
//        def id = column[String]("id")
//        private def loadEntity(id: String, id2: String) =
//            byId[E](id).get
//        private def getId(e: E) =
//            Option(e.id, e.id)
//        def * = id ~ id <> (loadEntity _, getId _)
//    }
//
//    private val tablesThreadLocal =
//        new ThreadLocal[MutableMap[Entity, EntitySlickTable[_]]] {
//            override def initialValue = MutableMap()
//        }
//
//    implicit def entitySlickTableToEntityMock[E <: Entity: Manifest](table: EntitySlickTable[E]) =
//        table.entityMock
//
//    implicit def entityValueToColumn[V <% EntityValue[V]](value: V) = {
//        val ref = StatementMocks.lastFakeVarCalled.get
//        val table = tablesThreadLocal.get()(ref.outerEntity)
//        table.column[String](ref.name)
//    }
//
//    def slickTable[E <: Entity: Manifest] = {
//        val entityName = EntityHelper.getEntityName(manifest[E].runtimeClass)
//        new EntitySlickTable[E]
//    }
//}
//
//object Test extends App {
//
//    import ctx._
//
//    import driver.simple._
//
//    class MyEntity(var i: Int, var d: Date) extends Entity {
//        var s = "s"
//    }
//
//    class MyMigration extends Migration {
//
//        def timestamp = System.currentTimeMillis
//
//        def up = {
//            removeAllEntitiesTables.ifExists
//            createTableForAllEntities.ifNotExists
//        }
//
//    }
//
//    val entity =
//        transactional {
//            new MyEntity(213, new Date)
//        }
//
//    transactional {
////        database.withSession {
////            implicit session: Session =>
////                val q = slickTable[MyEntity].map(a => entityValueToColumn(a.s))
////                val l = q.to[List]
////                println(l)
////        }
//                val qr = Queryable[MyEntity]
//                val q =
//                    for {
//                        c <- qr if c.i == 213
//                    } yield (c.i, c.d, c.s, c)
//                val l = q.toSeq
//                println(l)
//    }
//}
