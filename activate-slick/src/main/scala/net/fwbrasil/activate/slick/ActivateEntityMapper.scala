//package net.fwbrasil.activate.slick
//
//import scala.reflect.runtime.universe.Mirror
//import scala.slick.direct.Mapper
//import net.fwbrasil.activate.entity.EntityMetadata
//import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom
//import net.fwbrasil.activate.storage.relational.idiom.postgresqlDialect
//import net.fwbrasil.activate.storage.relational.idiom.mySqlDialect
//import net.fwbrasil.activate.storage.relational.idiom.derbyDialect
//import net.fwbrasil.activate.storage.relational.idiom.h2Dialect
//import net.fwbrasil.activate.storage.relational.idiom.hsqldbDialect
//import net.fwbrasil.activate.storage.relational.idiom.oracleDialect
//import net.fwbrasil.activate.storage.relational.idiom.db2Dialect
//
//class ActivateEntityMapper(val dialect: SqlIdiom)(implicit val mirror: Mirror) extends Mapper {
//    private def metadataOption(tpe: reflect.runtime.universe.Type): Option[EntityMetadata] =
//        ActivateSlickBackend.entityMetadataOption(tpe)
//    def fieldToColumn(sym: reflect.runtime.universe.Symbol): String = {
//        val ms = sym.asInstanceOf[scala.reflect.internal.Symbols#MethodSymbol]
//        val ownerClass =
//            if (ms.rawowner.isClass)
//            	ms.rawowner.toType
//            else
//                ms.rawowner.rawowner.toType
//        val name = 
//            metadataOption(ownerClass.asInstanceOf[scala.reflect.runtime.universe.Type]).get
//            .propertiesMetadata.find(_.originalName == sym.name.toString).get.name
//        normalizeIfNecessary(name)
//    }
//    def isMapped(tpe: reflect.runtime.universe.Type): Boolean =
//        metadataOption(tpe).isDefined
//    def typeToTable(tpe: reflect.runtime.universe.Type): String =
//        normalizeIfNecessary(metadataOption(tpe).get.name)
//        
//    def normalizeIfNecessary(string: String) = {
//        dialect match {
//            case dialect: derbyDialect.type =>
//                string.toUpperCase
//            case dialect: h2Dialect.type =>
//                string.toUpperCase
//            case dialect: hsqldbDialect.type =>
//                string.toUpperCase
//            case dialect: oracleDialect.type =>
//                dialect.normalize(string)
//            case dialect: db2Dialect.type =>
//                string.toUpperCase
//            case other =>
//                string
//        }
//    }
//}
//
