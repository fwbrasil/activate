package net.fwbrasil.activate.util

import scala.reflect.Manifest
import java.{ lang => jl }

object ManifestUtil {

	implicit def classToManifest[C](clazz: Class[C]): Manifest[C] =
		manifestClass(clazz).asInstanceOf[Manifest[C]]

	def manifestToClass[T](manifest: Manifest[T]) =
		manifest.erasure.asInstanceOf[Class[T]]

	def erasureOf[T: Manifest] =
		manifest[T].erasure.asInstanceOf[Class[T]]

	val ByteClass = classOf[scala.Byte]
	val ShortClass = classOf[scala.Short]
	val CharClass = classOf[scala.Char]
	val IntClass = classOf[scala.Int]
	val LongClass = classOf[scala.Long]
	val FloatClass = classOf[scala.Float]
	val DoubleClass = classOf[scala.Double]
	val BooleanClass = classOf[scala.Boolean]
	val NullClass = classOf[scala.Null]
	val UnitClass = classOf[scala.Unit]

	def manifestClass[T](c: Class[_]) = (c match {
		case jl.Byte.TYPE | ByteClass => Manifest.Byte
		case jl.Short.TYPE | ShortClass => Manifest.Short
		case jl.Character.TYPE | CharClass => Manifest.Char
		case jl.Integer.TYPE | IntClass => Manifest.Int
		case jl.Long.TYPE | LongClass => Manifest.Long
		case jl.Float.TYPE | FloatClass => Manifest.Float
		case jl.Double.TYPE | DoubleClass => Manifest.Double
		case jl.Boolean.TYPE | BooleanClass => Manifest.Boolean
		case jl.Void.TYPE | UnitClass => Manifest.Unit
		case null | NullClass => Manifest.Null
		case x => Manifest.classType(x)
	}).asInstanceOf[Manifest[T]]

}