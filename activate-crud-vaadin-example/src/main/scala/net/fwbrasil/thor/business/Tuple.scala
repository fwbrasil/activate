package net.fwbrasil.thor.business

import net.fwbrasil.thor.thorContext._

class TupleTemplate extends Entity {
	def columns =
		executeQuery {
			(e: TupleTemplateColumn) => where(e.template :== this) select (e) orderBy (e.order)
		}
}

object TupleTemplate {
	def apply(cols: String*) = {
		val template = new TupleTemplate
		var i = 0
		for (i <- 0 until cols.size)
			new TupleTemplateColumn(template, cols(i).toUpperCase(), i)
		template
	}
}

case class TupleTemplateColumn private[business] (template: TupleTemplate, name: String, order: Int) extends Entity

case class ThorTuple(collection: TupleCollection, v1: String, v2: String, v3: String) extends Entity {
	override def equals(obj: Any) =
		obj.isInstanceOf[ThorTuple] && equals(obj.asInstanceOf[ThorTuple])
	def equals(other: ThorTuple) =
		other != null && other.v1 == v1 && other.v2 == v2 && other.v3 == v3

}

object ThorTuple {
	def apply(collection: TupleCollection, values: List[String]) =
		values.size match {
			case 0 =>
				new ThorTuple(collection, null, null, null)
			case 1 =>
				new ThorTuple(collection, values(0), null, null)
			case 2 =>
				new ThorTuple(collection, values(0), values(1), null)
			case 3 =>
				new ThorTuple(collection, values(0), values(1), values(2))
		}
}

class TupleCollection(val template: TupleTemplate) extends Entity {
	def tuples = allWhere[ThorTuple](_.collection :== this)
}

object TupleCollection {
	def apply(template: TupleTemplate, rows: List[List[String]]) = {
		val coll = new TupleCollection(template)
		for (row <- rows)
			ThorTuple(coll, row)
		coll
	}
}