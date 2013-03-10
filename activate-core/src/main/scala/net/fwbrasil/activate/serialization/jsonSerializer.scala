package net.fwbrasil.activate.serialization

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver

object jsonSerializer extends Serializer {

    @transient
    var _xStream = new XStream(new JettisonMappedXmlDriver)

    def xStream = {
        if (_xStream == null)
            _xStream = new XStream(new JettisonMappedXmlDriver)
        _xStream
    }

    def toSerialized[T: Manifest](value: T): Array[Byte] =
        xStream.toXML(value).getBytes

    def fromSerialized[T: Manifest](bytes: Array[Byte]): T =
        xStream.fromXML(new String(bytes)).asInstanceOf[T]
}