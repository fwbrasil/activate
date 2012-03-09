package net.fwbrasil.thor.business

import net.fwbrasil.thor.thorContext._
import java.sql.DriverManager
import java.sql.ResultSet
import scala.collection.mutable.{ ListBuffer => MutableList }
import org.joda.time.DateTime

trait Source extends Entity {
	def updateAndExtract = {
		val lastExtraction =
			(executeQuery {
				(se: SourceExtraction) => where(se.source :== this) select (se) orderBy (se.date)
			}).lastOption
		(if (lastExtraction.isEmpty || lastExtraction.get.date.plus(updateIntervalInMinutes).isBeforeNow())
			new SourceExtraction(this, extract, DateTime.now)
		else
			lastExtraction.get).collection
	}
	var tupleTemplate: TupleTemplate
	protected def extract: TupleCollection
	var updateIntervalInMinutes: Int = 1
}

class SourceExtraction(
	val source: Source, val collection: TupleCollection, val date: DateTime) extends Entity

class FixedSource(
		var tupleTemplate: TupleTemplate,
		rows: List[List[String]]) extends Source {
	def extract = collection
	var collection = TupleCollection(tupleTemplate, rows)
}

class EmptySource(
	tupleTemplate: TupleTemplate) extends FixedSource(tupleTemplate, List())
