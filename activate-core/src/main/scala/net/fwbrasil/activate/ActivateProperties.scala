package net.fwbrasil.activate

import java.util.Properties
import scala.util.PropertiesTrait

class ActivateProperties(
    parent: Option[ActivateProperties],
    prefix: Option[String],
    pGetProperty: String => Option[String] = (name: String) => Option(System.getProperty(name))) {

    def basePath: List[String] =
        parent.map(_.basePath).getOrElse(List()) ++ prefix.toList

    def fullPath(path: String*) =
        (basePath ++ path.toList).mkString(".")

    def getRequiredProperty(path: String*): String = {
        val property = fullPath(path: _*)
        getProperty(path: _*).getOrElse(throw new IllegalStateException("Cant find property " + property))
    }

    def getProperty(path: String*): Option[String] = {
        val property = fullPath(path: _*)
        pGetProperty(property)
    }
}