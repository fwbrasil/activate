package net.fwbrasil.activate

object EscapeProblem extends App {

	def escape(string: String) =
		"\"" + string + "\""

	println("p" + escape("a") + "c")

}