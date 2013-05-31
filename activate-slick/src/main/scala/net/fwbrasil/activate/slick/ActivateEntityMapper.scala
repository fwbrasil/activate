package net.fwbrasil.activate.slick

import scala.reflect.runtime.universe.Mirror
import scala.slick.direct.Mapper

import net.fwbrasil.activate.entity.EntityMetadata

class ActivateEntityMapper(implicit val mirror: Mirror) extends Mapper {
    private def metadataOption(tpe: reflect.runtime.universe.Type): Option[EntityMetadata] =
        ActivateSlickBackend.entityMetadataOption(tpe)
    def fieldToColumn(sym: reflect.runtime.universe.Symbol): String = {
        val ownerClass =
            if (sym.owner.isClass)
                sym.owner.typeSignature
            else
                sym.owner.owner.typeSignature
        metadataOption(ownerClass).get
            .propertiesMetadata.find(_.originalName == sym.name.toString).get.name
    }
    def isMapped(tpe: reflect.runtime.universe.Type): Boolean =
        metadataOption(tpe).isDefined
    def typeToTable(tpe: reflect.runtime.universe.Type): String =
        metadataOption(tpe).get.name
}

