package net.fwbrasil.thor.business

import net.fwbrasil.thor.thorContext._
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.DataInputStream
import scala.collection.mutable.MutableList

class FileSource(
		var layout: FileLayout,
		var location: FileLocation) extends Source {
	var tupleTemplate =
		layout.tupleTemplate(location.fileStream)
	def extract =
		layout.readCollection(tupleTemplate, location.fileStream)

}

trait FileLayout extends Entity {
	def tupleTemplate(stream: FileStream): TupleTemplate
	def readCollection(tupleTemplate: TupleTemplate, stream: FileStream) = TupleCollection(tupleTemplate, read(stream))
	def read(stream: FileStream): List[List[String]]
}

class CharacterSeparatedFileLayout(
		var separationCharacter: Char) extends FileLayout {
	private def getTokens(line: String) =
		line.split(separationCharacter)
	def tupleTemplate(stream: FileStream) = {
		val line = stream.readLine
		assert(line != null)
		val tokens = getTokens(line)
		TupleTemplate(tokens: _*)
	}
	def read(stream: FileStream) = {
		// Pula cabecalho
		stream.readLine
		var line: String = null
		def readLine = {
			line = stream.readLine
			line
		}
		var result = MutableList[List[String]]()
		while (readLine != null) {
			result += getTokens(line).toList
		}
		result.toList
	}

}

protected class FileStream(path: String) {
	val fileInputStream = new FileInputStream(path)
	val in = new DataInputStream(fileInputStream);
	val br = new BufferedReader(new InputStreamReader(in));
	def readLine =
		br.readLine
}

trait FileLocation extends Entity {
	def fileStream: FileStream
}

class LocalFileLocation(
		var absolutePath: String) extends FileLocation {
	def fileStream =
		new FileStream(absolutePath)

}
