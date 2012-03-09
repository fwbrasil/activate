package net.fwbrasil.thor

import thorContext._
import business._

object Main extends App {

	transactional {
		val conn =
			new JdbcConnection(
				jdbcDriver = "com.mysql.jdbc.Driver",
				user = "root",
				password = "",
				url = "jdbc:mysql://127.0.0.1/ACTIVATE_TEST")

		val constructors = classOf[JdbcConnection].getConstructors()
		println(constructors)

		val sourceA =
			new SqlQuerySource(
				sql = "select id, attribute from traitAttribute1",
				jdbcConnection = conn)

		val sourceB = new FixedSource(
			tupleTemplate = TupleTemplate("id", "attribute"),
			rows = List(List("id", "att")))

		//		val layout =
		//			new CharacterSeparatedFileLayout(
		//				separationCharacter = ';')
		//
		//		val location = new LocalFileLocation(
		//			absolutePath = "/Users/fwbrasil/csvFile.csv")
		//
		//		val sourceB =
		//			new FileSource(
		//				layout = layout,
		//				location = location)

		val tyr = new Tyr(
			sourceA = sourceA,
			sourceB = sourceB)
		val res = tyr.combat

		println(res)
	}
}