package net.fwbrasil.activate.util

import scala.tools.scalap.scalax.rules.scalasig._
import scala.collection.mutable.{ HashMap, SynchronizedMap }

object Meta {
	def fail(msg: String) = throw new IllegalStateException(msg)
}
object ScalaSigReader {

	val cache = new HashMap[Class[_], Option[ScalaSig]]() with SynchronizedMap[Class[_], Option[ScalaSig]]

	def readConstructor(argName: String, clazz: Class[_], typeArgIndex: Int, argNames: List[String]): Class[_] = {
		val cl = findClass(clazz)
		val cstr = findConstructor(cl, argNames).getOrElse(Meta.fail("Can't find constructor for " + clazz))
		findArgType(cstr, argNames.indexOf(argName), typeArgIndex)
	}

	def readField(name: String, clazz: Class[_], typeArgIndex: Int): Class[_] = {
		def read(current: Class[_]): MethodSymbol = {
			if (current == null)
				Meta.fail("Can't find field " + name + " from " + clazz)
			else
				findField(findClass(current), name).getOrElse(read(current.getSuperclass))
		}
		findArgTypeForField(read(clazz), typeArgIndex)
	}

	def isVisibleFrom(source: Class[_], target: Class[_], methodSymbol: MethodSymbol): Boolean = {
		val sourcePackage = source.getPackage.getName
		val privateWithinOption = methodSymbol.symbolInfo.privateWithin
		if (privateWithinOption.isDefined) {
			val targetPackage = privateWithinOption.get.toString
			sourcePackage.startsWith(targetPackage)
		} else if (methodSymbol.isProtected) {
			target.isAssignableFrom(source)
		} else if (methodSymbol.isPrivate) {
			target == source
		} else
			true
	}

	def findClass(clazz: Class[_]): ClassSymbol = {
		val sig = findScalaSig(clazz).getOrElse(Meta.fail("Can't find ScalaSig for " + clazz))
		findClass(sig, clazz).getOrElse(Meta.fail("Can't find " + clazz + " from parsed ScalaSig"))
	}

	private def findClass(sig: ScalaSig, clazz: Class[_]): Option[ClassSymbol] = {
		sig.symbols.collect { case c: ClassSymbol if !c.isModule => c }.find(_.name == clazz.getSimpleName).orElse {
			sig.topLevelClasses.find(_.symbolInfo.name == clazz.getSimpleName).orElse {
				sig.topLevelObjects.map { obj =>
					val t = obj.infoType.asInstanceOf[TypeRefType]
					t.symbol.children collect { case c: ClassSymbol => c } find (_.symbolInfo.name == clazz.getSimpleName)
				}.head
			}
		}
	}

	def findConstructor(c: ClassSymbol, argNames: List[String]): Option[MethodSymbol] = {
		val ms = c.children collect { case m: MethodSymbol if m.name == "<init>" => m }
		ms.find(m => m.children.map(_.name) == argNames)
	}

	def findMethod(c: Symbol, name: String, argNames: List[String]): Option[MethodSymbol] = {
		val ms = c.children collect { case m: MethodSymbol if m.name == name => m }
		ms.find(m => m.children.map(_.name) == argNames)
	}

	def findCompanionObjectMethod(clazz: Class[_], name: String, argNames: List[String]): Option[MethodSymbol] = {
		val ss = findScalaSig(clazz).get
		val os = (ss.topLevelObjects collect { case o: ObjectSymbol if o.name == clazz.getSimpleName => o }).headOption
		if (os.isDefined) {
			val ti = os.get.infoType.asInstanceOf[TypeRefType].symbol
			findMethod(ti, name, argNames)
		} else None
	}

	def findField(c: ClassSymbol, name: String): Option[MethodSymbol] =
		(c.children collect { case m: MethodSymbol if m.name == name => m }).headOption

	private def findArgType(s: MethodSymbol, argIdx: Int, typeArgIndex: Int): Class[_] = {
		def findPrimitive(t: Type): Symbol = t match {
			case TypeRefType(ThisType(_), symbol, _) if isPrimitive(symbol) => symbol
			case TypeRefType(_, _, TypeRefType(ThisType(_), symbol, _) :: xs) => symbol
			case TypeRefType(_, symbol, Nil) => symbol
			case TypeRefType(_, _, args) =>
				args(typeArgIndex) match {
					case ref @ TypeRefType(_, _, _) => findPrimitive(ref)
					case x => Meta.fail("Unexpected type info " + x)
				}
			case x => Meta.fail("Unexpected type info " + x)
		}
		toClass(findPrimitive(s.children(argIdx).asInstanceOf[SymbolInfoSymbol].infoType))
	}

	private def findArgTypeForField(s: MethodSymbol, typeArgIdx: Int): Class[_] = {
		val t = s.infoType.asInstanceOf[{ def resultType: Type }].resultType match {
			case TypeRefType(_, _, args) => args(typeArgIdx)
		}

		def findPrimitive(t: Type): Symbol = t match {
			case TypeRefType(ThisType(_), symbol, _) => symbol
			case ref @ TypeRefType(_, _, _) => findPrimitive(ref)
			case x => Meta.fail("Unexpected type info " + x)
		}
		toClass(findPrimitive(t))
	}

	private def toClass(s: Symbol) = s.path match {
		case "scala.Short" => classOf[Short]
		case "scala.Int" => classOf[Int]
		case "scala.Long" => classOf[Long]
		case "scala.Boolean" => classOf[Boolean]
		case "scala.Float" => classOf[Float]
		case "scala.Double" => classOf[Double]
		case _ => classOf[AnyRef]
	}

	private def isPrimitive(s: Symbol) = toClass(s) != classOf[AnyRef]

	def findScalaSig(clazz: Class[_]): Option[ScalaSig] =
		cache.getOrElseUpdate(clazz, ScalaSigParser.parse(clazz).orElse(findScalaSig(clazz.getDeclaringClass)))

}