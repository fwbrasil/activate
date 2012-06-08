package net.fwbrasil.activate

import java.util.Properties
import scala.util.PropertiesTrait

class ActivateProperties(parent: Option[ActivateProperties], prefix: String) {

	def properties: Map[String, String] =
		parent.map(_.properties).getOrElse(
			propertiesToMap(System.getProperties))

	private def propertiesToMap(props: Properties) = {
		var hm = Map[String, String]()
		val e = props.keys
		while (e.hasMoreElements) {
			val s = e.nextElement.asInstanceOf[String]
			hm += (s -> props.getProperty(s))
		}
		hm
	}

	def basePath: List[String] =
		parent.map(_.basePath).getOrElse(List()) ++ List(prefix)

	def fullPath(path: String*) =
		(basePath ++ path.toList).mkString(".")

	def getProperty(path: String*) = {
		val property = fullPath(path: _*)
		properties.getOrElse(property, throw new IllegalStateException("Cant find property " + property))
	}

	def childProperties(path: String*) = {
		val base = fullPath(path: _*) + "."
		properties.filterKeys(_.startsWith(base)).map(tuple => (tuple._1.replaceFirst(base, ""), tuple._2))
	}
}