package net.fwbrasil.activate.entity

import javassist.CtClass
import javassist.ClassPool
import javassist.CtField
import javassist.CtPrimitiveType
import javassist.expr.ExprEditor
import javassist.ClassClassPath
import java.lang.management.ManagementFactory
import javassist.Modifier
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.Reflection.toNiceObject
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.GraphUtil.DependencyTree
import javassist.expr.FieldAccess
import javassist.bytecode.SignatureAttribute
import javassist.bytecode.CodeAttribute
import javassist.bytecode.LocalVariableAttribute
import javassist.CtBehavior
import net.fwbrasil.activate.ActivateContext
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.util.RichList._

import javassist.CtConstructor

object EntityEnhancer extends Logging {

	val varClassName = classOf[Var[_]].getName
	val idVarClassName = classOf[IdVar].getName
	val hashMapClassName = classOf[java.util.HashMap[_, _]].getName
	val entityClassName = classOf[Entity].getName
	val entityClassFieldPrefix = entityClassName.replace(".", "$")
	val scalaVariables = Array("$outer", "bitmap$")
	val validEntityFields = Array("invariants", "listener")

	def isEntityClass(clazz: CtClass, classPool: ClassPool): Boolean =
		clazz.getInterfaces.contains(classPool.get(entityClassName)) ||
			(clazz.getSuperclass != null && (isEntityClass(clazz.getSuperclass, classPool) || !clazz.getInterfaces.find((interface: CtClass) => isEntityClass(interface, classPool)).isEmpty))

	def isEntityTraitField(field: CtField) =
		field.getName.startsWith(entityClassFieldPrefix)

	def isVarField(field: CtField) =
		field.getType.getName == varClassName

	def isScalaVariable(field: CtField) =
		scalaVariables.filter((name: String) => field.getName.startsWith(name)).nonEmpty

	def isValidEntityField(field: CtField) =
		validEntityFields.filter((name: String) => field.getName == name).nonEmpty

	def isTransient(field: CtField) =
		Modifier.isTransient(field.getModifiers)

	def isCandidate(field: CtField) =
		!isTransient(field) && !isEntityTraitField(field) && !isVarField(field) && !isScalaVariable(field) && !isValidEntityField(field)

	def removeLazyValueValue(fieldsToEnhance: Array[CtField]) = {
		val lazyValueValueSuffix = "Value"
		val lazyValues = fieldsToEnhance.filter((field: CtField) => fieldsToEnhance.filter(_.getName() == field.getName() + lazyValueValueSuffix).nonEmpty)
		fieldsToEnhance.filter((field: CtField) => lazyValues.filter(_.getName() + lazyValueValueSuffix == field.getName()).isEmpty)
	}

	def isEnhanced(clazz: CtClass) =
		clazz.getDeclaredFields.filter(_.getName() == "varTypes").nonEmpty

	def box(typ: CtClass, ref: String) =
		typ match {
			case typ: CtPrimitiveType =>
				"new " + typ.getWrapperName + "($" + ref + ")"
			case other =>
				"$" + ref
		}

	def enhance(clazz: CtClass, classPool: ClassPool): Set[CtClass] = {
		if (!clazz.isInterface() && !clazz.isFrozen && !isEnhanced(clazz) && isEntityClass(clazz, classPool)) {
			try {
				val allFields = clazz.getDeclaredFields
				val fieldsToEnhance = removeLazyValueValue(allFields.filter(isCandidate))
				val enhancedFieldsMap =
					(for (originalField <- fieldsToEnhance) yield {
						enhanceField(clazz, classPool, originalField)
					}).toMap
				enhanceConstructors(clazz, enhancedFieldsMap)
				enhanceFieldsAccesses(clazz, enhancedFieldsMap)
				createVarTypesField(clazz, classPool, enhancedFieldsMap)
			} catch {
				case e =>
					val toThrow = new IllegalStateException("Fail to enhance " + clazz.getName)
					toThrow.initCause(e)
					throw toThrow
			}
			//			clazz.writeFile
			enhance(clazz.getSuperclass, classPool) + clazz
		} else
			Set()
	}

	private var _enhancedEntityClasses: Option[Set[Class[Entity]]] = None

	def enhancedEntityClasses(referenceClass: Class[_]) = synchronized {
		_enhancedEntityClasses.getOrElse {
			val classPool = buildClassPool
			val enhancedEntityClasses =
				entityClassesNames(referenceClass)
					.map(enhance(_, classPool)).flatten
			val resolved = resolveDependencies(enhancedEntityClasses)
			val res = materializeClasses(resolved)
			_enhancedEntityClasses = Some(res)
			res
		}
	}

	private def enhance(clazzName: String, classPool: ClassPool): Set[CtClass] =
		enhance(classPool.get(clazzName), classPool)

	private def materializeClasses(resolved: List[CtClass]) = {
		val classLoader = classOf[Entity].getClassLoader
		(for (enhancedEntityClass <- resolved)
			yield enhancedEntityClass.toClass(classLoader).asInstanceOf[Class[Entity]]).toSet
	}

	private def entityClassesNames(referenceClass: Class[_]) =
		Reflection.getAllImplementorsNames(List(classOf[ActivateContext], referenceClass: Class[_]), classOf[Entity])

	private def buildClassPool = {
		val classPool = ClassPool.getDefault
		classPool.appendClassPath(new ClassClassPath(this.niceClass))
		classPool
	}

	private def resolveDependencies(enhancedEntityClasses: Set[CtClass]) = {
		val tree = new DependencyTree(enhancedEntityClasses)
		for (enhancedEntityClass <- enhancedEntityClasses)
			registerDependency(enhancedEntityClass, tree, enhancedEntityClasses)
		tree.resolve
	}

	private def registerDependency(clazz: CtClass, tree: DependencyTree[CtClass], enhancedEntityClasses: Set[CtClass]): Unit = {
		val superClass = clazz.getSuperclass()
		if (superClass != null) {
			if (enhancedEntityClasses.contains(superClass))
				tree.addDependency(superClass, clazz)
			registerDependency(superClass, tree, enhancedEntityClasses)
		}
	}

	private def localVariablesMap(codeAttribute: CodeAttribute) = {
		val table = codeAttribute.getAttribute(LocalVariableAttribute.tag).asInstanceOf[LocalVariableAttribute]
		if (table != null)
			(for (i <- 0 until table.tableLength)
				yield (table.variableName(i) -> i)).toMap
		else
			Map[String, Int]()
	}

	private def enhanceField(clazz: CtClass, classPool: ClassPool, originalField: CtField) = {
		val varClazz = classPool.get(varClassName);
		val name = originalField.getName
		clazz.removeField(originalField)
		val enhancedField = new CtField(varClazz, name, clazz);
		enhancedField.setModifiers(originalField.getModifiers)
		clazz.addField(enhancedField)
		val originalFieldTypeAndOptionFlag =
			if (originalField.getType.getName != classOf[Option[_]].getName)
				(originalField.getType, false)
			else {
				val att = originalField.getFieldInfo().getAttribute(SignatureAttribute.tag).asInstanceOf[SignatureAttribute]
				val sig = att.getSignature
				val className = sig.substring(15, sig.size - 3).replaceAll("/", ".")
				(classPool.getCtClass(className), true)
			}
		(enhancedField, originalFieldTypeAndOptionFlag)
	}

	private def createVarTypesField(clazz: CtClass, classPool: ClassPool, enhancedFieldsMap: Map[javassist.CtField, (javassist.CtClass, Boolean)]) = {
		val init = clazz.makeClassInitializer()
		val hashMapClass = classPool.get(hashMapClassName)
		val varTypesField = new CtField(hashMapClass, "varTypes", clazz);
		varTypesField.setModifiers(Modifier.STATIC)
		clazz.addField(varTypesField, "new " + hashMapClassName + "();")
		val initBody =
			(for ((field, (typ, optionFlag)) <- enhancedFieldsMap)
				yield "varTypes.put(\"" + field.getName.split('$').last + "\", " + typ.getName + ".class)").mkString(";") + ";"

		init.insertBefore(initBody)
	}

	private def enhanceConstructors(clazz: CtClass, enhancedFieldsMap: Map[CtField, (CtClass, Boolean)]) = {
		for (c <- clazz.getDeclaredConstructors) yield {
			val codeAttribute = c.getMethodInfo.getCodeAttribute
				def superCallIndex = codeAttribute.iterator.skipConstructor
			val isPrimaryConstructor = codeAttribute.iterator.skipSuperConstructor > 0
			val fields = ListBuffer[CtField]()
			c.instrument(new ExprEditor {
				override def edit(fa: FieldAccess) = {
					val isWriter = fa.isWriter
					val isBeforeSuperCall = fa.indexOfBytecode < superCallIndex
					val isEnhancedField = enhancedFieldsMap.contains(fa.getField)
					if (isWriter && isEnhancedField && isBeforeSuperCall && isPrimaryConstructor) {
						fields += fa.getField
						fa.replace("")
					} else {
						val isEnhancedField = enhancedFieldsMap.contains(fa.getField)
						if (isEnhancedField) {
							val (typ, optionFlag) = enhancedFieldsMap.get(fa.getField).get
							enhanceFieldAccess(fa, typ, optionFlag)
						}
					}
				}
			})
			if (isPrimaryConstructor) {
				var replace = "setInitialized();setNotInvalid();\n"
				for ((field, (typ, optionFlag)) <- enhancedFieldsMap) {
					if (field.getName == "id") {
						replace += "this." + field.getName + " = new " + idVarClassName + "(this);\n"
						replace += "this.net$fwbrasil$activate$entity$Entity$_setter_$id_$eq(null);\n"
					} else {
						val isMutable = Modifier.isFinal(field.getModifiers)
						replace += "this." + field.getName + " = new " + varClassName + "(" + isMutable + "," + typ.getName + ".class, \"" + field.getName.split('$').last + "\", this);\n"
					}
				}

				val localsMap = localVariablesMap(codeAttribute)
				for (field <- fields) {
					val (typ, optionFlag) = enhancedFieldsMap.get(field).get
					if (optionFlag)
						replace += "this." + field.getName + ".put(" + box(typ, localsMap(field.getName).toString) + ");\n"
					else
						replace += "this." + field.getName + ".putValue(" + box(typ, localsMap(field.getName).toString) + ");\n"
				}

				c.insertBeforeBody(replace)
				c.insertAfter("if(this.getClass() == " + clazz.getName + ".class) {validateOnCreate();addToLiveCache();}\n")
			}
		}
	}

	private def enhanceFieldsAccesses(clazz: javassist.CtClass, enhancedFieldsMap: scala.collection.immutable.Map[javassist.CtField, (javassist.CtClass, Boolean)]): Unit = {

		clazz.instrument(
			new ExprEditor {
				override def edit(fa: FieldAccess) = {
					if (!fa.where.isInstanceOf[CtConstructor]) {
						val field =
							try {
								fa.getField
							} catch {
								case e: javassist.NotFoundException =>
									null
							}
						if (field != null && enhancedFieldsMap.contains(field)) {
							val (typ, optionFlag) = enhancedFieldsMap.get(fa.getField).get
							enhanceFieldAccess(fa, typ, optionFlag)
						}
					}
				}

			})
	}

	private def enhanceFieldAccess(fa: FieldAccess, typ: CtClass, optionFlag: Boolean) =
		if (fa.isWriter) {
			if (optionFlag)
				fa.replace("this." + fa.getFieldName + ".put(" + box(typ, "$") + ");")
			else
				fa.replace("this." + fa.getFieldName + ".putValue(" + box(typ, "$") + ");")
		} else if (fa.isReader) {
			if (optionFlag)
				fa.replace("$_ = ($r) this." + fa.getFieldName + ".get($$);")
			else
				fa.replace("$_ = ($r) this." + fa.getFieldName + ".getValue($$);")
		}

}
