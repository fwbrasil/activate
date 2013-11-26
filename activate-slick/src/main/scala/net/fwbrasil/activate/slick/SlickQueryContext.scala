package net.fwbrasil.activate.slick

import java.sql.Connection
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.activate.storage.relational.JdbcRelationalStorage
import net.fwbrasil.activate.storage.relational.idiom.db2Dialect
import net.fwbrasil.activate.storage.relational.idiom.derbyDialect
import net.fwbrasil.activate.storage.relational.idiom.h2Dialect
import net.fwbrasil.activate.storage.relational.idiom.hsqldbDialect
import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
import net.fwbrasil.activate.storage.relational.idiom.oracleDialect
import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
import net.fwbrasil.activate.util.ManifestUtil._
import language.implicitConversions
import scala.slick.driver.PostgresDriver
import scala.slick.driver.HsqldbDriver
import scala.slick.driver.MySQLDriver
import scala.slick.driver.H2Driver
import scala.slick.driver.DerbyDriver
import scala.slick.jdbc.JdbcBackend
import scala.slick.direct.AnnotationMapper.column
import scala.slick.ast.TypedType
import scala.reflect.ClassTag
import net.fwbrasil.activate.entity.Entity
import scala.slick.jdbc.JdbcType
import scala.reflect._
import net.fwbrasil.activate.entity.id.EntityId
import net.fwbrasil.activate.entity.StringEntityValue
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.LongEntityValue

trait SlickQueryContext {
    this: ActivateContext =>

    type Queryable[T] = slick.direct.Queryable[T]

    val storage: JdbcRelationalStorage

    lazy val driver =
        storage.asInstanceOf[JdbcRelationalStorage].dialect match {
            case d: derbyDialect =>
                DerbyDriver
            case d: h2Dialect =>
                H2Driver
            case d: hsqldbDialect =>
                HsqldbDriver
            case d: mySqlDialect =>
                MySQLDriver
            case d: postgresqlDialect =>
                PostgresDriver
            //            case d: oracleDialect.type =>
            //                OracleDriver
            //            case d: db2Dialect.type =>
            //                DB2Driver
        }

    import driver.simple._
    
    implicit def entityIdColumnType[E <: Entity: ClassTag] = {
        val entityValue = EntityId.idTvalFunctionFor(classTag[E].erasure)(None).asInstanceOf[EntityValue[_]]
        entityValue match {
            case value: StringEntityValue =>
            	driver.columnTypes.stringJdbcType
            case value: IntEntityValue =>
                driver.columnTypes.intJdbcType
            case value: LongEntityValue =>
                driver.columnTypes.longJdbcType
        }
    }.asInstanceOf[JdbcType[E#ID]]
    
    implicit def EntityMappedColumnType[E <: Entity: ClassTag] = MappedColumnType.base[E, E#ID](_.id, byId[E](_)(manifestClass(classTag[E].erasure)).get)
    
    class EntityTable[E <: Entity: Manifest](tag: Tag)
            extends Table[E](
                tag, EntityHelper.getEntityName(erasureOf[E])) {
        def mock = StatementMocks.mockEntity(erasureOf[E])
        def idColumn = column[E#ID]("id")
        def * = idColumn <> (fromId, (entity: E) => toId(entity))
        def / = ???
        def toId(e: E) =
            Some(e.id)
        def fromId(id: E#ID) =
            byId[E](id).get
    }
    val backend =
        new JdbcBackend.Database {
            def createConnection() = storage.directAccess.asInstanceOf[Connection]
        }

    def SlickQuery[E <: Entity: Manifest] =
        TableQuery[EntityTable[E]]

    val tableThreadLocal = new ThreadLocal[EntityTable[_]]

    implicit def tableToEntity[E <: Entity](table: EntityTable[E]) = {
        tableThreadLocal.set(table)
        table.mock
    }
    
    implicit def EntityColumnToFK[E <: Entity: Manifest](column: Column[E]) = {
        val table = tableThreadLocal.get
        val otherTable = TableQuery[EntityTable[E]]
        table.foreignKey("IGNORE", column, otherTable)(_.idColumn.asInstanceOf[Column[E]])
    }

    implicit class EntityInstance[E <: Entity](table: EntityTable[E])(implicit tm: TypedType[E]) {
        def instance =
            table.column[E]("id")
        def col = instance
    }

    implicit class EntityValueToColumn[V](value: V)(implicit tm: TypedType[V]) {
        def column: Column[V] = {
            val table = tableThreadLocal.get
            StatementMocks.lastFakeVarCalled.map {
                ref =>
                    if (ref.originVar != null)
                        if (ref.name == "id")
                            table.column[V](ref.originVar.name)
                        else
                            throw new UnsupportedOperationException("Slick queries doesn't support nested properties")
                    table.column[V](ref.name)
            }.getOrElse {
                throw new IllegalStateException("Invalid column")
            }
        }
        def col = column
    }

    implicit class QueryRun[T, U](query: Query[T, U]) {
        def execute =
            backend.withSession {
                session: Session =>
                    query.run(session).toList
            }
    }

}