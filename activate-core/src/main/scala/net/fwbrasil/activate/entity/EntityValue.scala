package net.fwbrasil.activate.entity

import java.util.{ Date, Calendar }
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import java.lang.{ Integer => JInteger, Boolean => JBoolean, Character => JCharacter, Float => JFloat, Double => JDouble }

abstract class EntityValue[V: Manifest](val value: Option[V]) extends Serializable

case class IntEntityValue(override val value: Option[Int])
	extends EntityValue(value)

case class BooleanEntityValue(override val value: Option[Boolean])
	extends EntityValue(value)

case class CharEntityValue(override val value: Option[Char])
	extends EntityValue(value)

case class StringEntityValue(override val value: Option[String])
	extends EntityValue(value)

case class EnumerationEntityValue[E <: Enumeration#Value: Manifest](override val value: Option[E])
	extends EntityValue[E](value) {
	def enumerationManifest = manifest[E]
	def enumerationClass = enumerationManifest.erasure
}

case class FloatEntityValue(override val value: Option[Float])
	extends EntityValue(value)

case class DoubleEntityValue(override val value: Option[Double])
	extends EntityValue(value)

case class BigDecimalEntityValue(override val value: Option[BigDecimal])
	extends EntityValue(value)

case class DateEntityValue(override val value: Option[java.util.Date])
	extends EntityValue(value)

case class CalendarEntityValue(override val value: Option[java.util.Calendar])
	extends EntityValue(value)

case class ByteArrayEntityValue(override val value: Option[Array[Byte]])
	extends EntityValue(value)

case class EntityInstanceEntityValue[E <: Entity: Manifest](override val value: Option[E])
	extends EntityValue[E](value) {
	def entityManifest = manifest[E]
	def entityClass = entityManifest.erasure
}

case class EntityInstanceReferenceValue[E <: Entity: Manifest](override val value: Option[String])
	extends EntityValue[String](value) {
	def entityManifest = manifest[E]
	def entityClass = entityManifest.erasure
}

object EntityValue extends ValueContext {
  
	private[activate] def tvalFunction[T](clazz: Class[_]): (Option[T]) => EntityValue[T] =
		(if(clazz == classOf[Int])
			(value: Option[Int]) => toIntEntityValueOption(value)
		else if(clazz == classOf[Boolean])
			(value: Option[Boolean]) => toBooleanEntityValueOption(value)
		else if(clazz == classOf[Char])
			(value: Option[Char]) => toCharEntityValueOption(value)
		else if(clazz == classOf[String])
			(value: Option[String]) => toStringEntityValueOption(value)
		else if(classOf[Enumeration#Value].isAssignableFrom(clazz))
			(value: Option[Enumeration#Value]) => toEnumerationEntityValueOption(value)(manifestClass(clazz))
		else if(clazz == classOf[Float])
			(value: Option[Float]) => toFloatEntityValueOption(value)
		else if(clazz == classOf[Double])
			(value: Option[Double]) => toDoubleEntityValueOption(value)
		else if(clazz == classOf[BigDecimal])
			(value: Option[BigDecimal]) => toBigDecimalEntityValueOption(value)
		else if(clazz == classOf[java.util.Date])
			(value: Option[Date]) => toDateEntityValueOption(value)
		else if(clazz == classOf[java.util.Calendar])
			(value: Option[Calendar]) => toCalendarEntityValueOption(value)
		else if(clazz == classOf[Array[Byte]])
			(value: Option[Array[Byte]]) => toByteArrayEntityValueOption(value)
		else if(classOf[Entity].isAssignableFrom(clazz))
			((value: Option[Entity]) => toEntityInstanceEntityValueOption(value)(manifestClass(clazz)))
        else
            ((value: Option[Object]) => (value match {
              case value: Option[Int] => toIntEntityValueOption(value)
              case value: Option[Boolean] => toBooleanEntityValueOption(value)
              case value: Option[Char] => toCharEntityValueOption(value)
              case value: Option[Float] => toFloatEntityValueOption(value)
              case value: Option[Double] => toDoubleEntityValueOption(value)
              case other => 
              	throw new IllegalStateException("Invalid entity value.")
            }).asInstanceOf[(T) => EntityValue[T]])).asInstanceOf[(Option[T]) => EntityValue[T]]
			
}
	
trait ValueContext {

	implicit def toIntEntityValue(value: Int) = 
		toIntEntityValueOption(Option(value))
	implicit def toBooleanEntityValue(value: Boolean) = 
		toBooleanEntityValueOption(Option(value))
	implicit def toCharEntityValue(value: Char) = 
		toCharEntityValueOption(Option(value))
	implicit def toStringEntityValue(value: String) = 
		toStringEntityValueOption(Option(value))
	implicit def toEnumerationEntityValue[E <: Enumeration#Value: Manifest](value: E): EnumerationEntityValue[E] = 
		toEnumerationEntityValueOption[E](Option(value))
	implicit def toFloatEntityValue(value: Float) = 
		toFloatEntityValueOption(Option(value))
	implicit def toDoubleEntityValue(value: Double) = 
		toDoubleEntityValueOption(Option(value))
	implicit def toBigDecimalEntityValue(value: BigDecimal) = 
		toBigDecimalEntityValueOption(Option(value))
	implicit def toDateEntityValue(value: java.util.Date) = 
		toDateEntityValueOption(Option(value))
	implicit def toCalendarEntityValue(value: java.util.Calendar) =
		toCalendarEntityValueOption(Option(value))
	implicit def toByteArrayEntityValue(value: Array[Byte]) = 
		toByteArrayEntityValueOption(Option(value))
	implicit def toEntityInstanceEntityValue[E <: Entity: Manifest](value: E) = 
		toEntityInstanceEntityValueOption(Option(value))
	
	implicit def toIntEntityValueOption(value: Option[Int]) = 
		IntEntityValue(value)
	implicit def toBooleanEntityValueOption(value: Option[Boolean]) = 
		BooleanEntityValue(value)
	implicit def toCharEntityValueOption(value: Option[Char]) = 
		CharEntityValue(value)
	implicit def toStringEntityValueOption(value: Option[String]) = 
		StringEntityValue(value)
	implicit def toEnumerationEntityValueOption[E <: Enumeration#Value: Manifest](value: Option[E]): EnumerationEntityValue[E] = 
		EnumerationEntityValue[E](value)
	implicit def toFloatEntityValueOption(value: Option[Float]) = 
		FloatEntityValue(value)
	implicit def toDoubleEntityValueOption(value: Option[Double]) = 
		DoubleEntityValue(value)
	implicit def toBigDecimalEntityValueOption(value: Option[BigDecimal]) = 
		BigDecimalEntityValue(value)
	implicit def toDateEntityValueOption(value: Option[java.util.Date]) = 
		DateEntityValue(value)
	implicit def toCalendarEntityValueOption(value: Option[java.util.Calendar]) =
		CalendarEntityValue(value)
	implicit def toByteArrayEntityValueOption(value: Option[Array[Byte]]) = 
		ByteArrayEntityValue(value)
	implicit def toEntityInstanceEntityValueOption[E <: Entity: Manifest](value: Option[E]): EntityInstanceEntityValue[E] = 
		EntityInstanceEntityValue(value)
	
}