package net.fwbrasil.activate.slick

import java.sql.Connection

import scala.collection.mutable.{Map => MutableMap}
import scala.slick.driver.DerbyDriver
import scala.slick.driver.H2Driver
import scala.slick.driver.HsqldbDriver
import scala.slick.driver.MySQLDriver
import scala.slick.driver.PostgresDriver
import scala.slick.session.Database
import scala.slick.session.Session

import com.typesafe.slick.driver.db2.DB2Driver
import com.typesafe.slick.driver.oracle.OracleDriver

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
import net.fwbrasil.activate.util.ManifestUtil.erasureOf

import language.implicitConversions

trait SlickQueryContext {
    this: ActivateContext =>
        
    type Queryable[T] = slick.direct.Queryable[T]

    val storage: JdbcRelationalStorage
    lazy val driver =
        storage.asInstanceOf[JdbcRelationalStorage].dialect match {
            case d: db2Dialect.type =>
                DB2Driver
            case d: derbyDialect.type =>
                DerbyDriver
            case d: h2Dialect.type =>
                H2Driver
            case d: hsqldbDialect.type =>
                HsqldbDriver
            case d: mySqlDialect.type =>
                MySQLDriver
            case d: oracleDialect.type =>
                OracleDriver
            case d: postgresqlDialect.type =>
                PostgresDriver
        }

    import driver.simple._

    implicit class QueryableToSeq[T](queryable: Queryable[T]) {
        def toSeq =
            database.withSession {
                session: Session =>
                    backend.result(queryable, session).toSeq
            }
    }

    implicit val mirror = scala.reflect.runtime.currentMirror

    lazy val backend = new ActivateSlickBackend(driver, storage.asInstanceOf[JdbcRelationalStorage].dialect, new ActivateEntityMapper)

    lazy val database =
        new Database {
            override def createConnection = storage.directAccess.asInstanceOf[Connection]
        }

}