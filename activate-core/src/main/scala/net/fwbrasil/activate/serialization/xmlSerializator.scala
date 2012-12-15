package net.fwbrasil.activate.serialization

import com.thoughtworks.xstream.XStream

object xmlSerializator extends Serializator {

	@transient
	var _xStream = new XStream()

	def xStream = {
		if (_xStream == null)
			_xStream = new XStream()
		_xStream
	}

	def toSerialized[T: Manifest](value: T): Array[Byte] =
		xStream.toXML(value).getBytes

	def fromSerialized[T: Manifest](bytes: Array[Byte]): T =
		xStream.fromXML(new String(bytes)).asInstanceOf[T]

}