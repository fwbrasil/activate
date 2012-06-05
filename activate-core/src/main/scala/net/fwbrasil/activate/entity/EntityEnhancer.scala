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

object EntityEnhancer extends Logging {

	def verifyNoVerify = {
		val RuntimemxBean = ManagementFactory.getRuntimeMXBean
		val arguments = RuntimemxBean.getInputArguments
		if (List(arguments.toArray: _*).filter(_ == "-Xverify:none").isEmpty) {
			val msg = "Please add -noverify to vm options"
			error(msg)
			throw new IllegalStateException(msg)
		}
	}

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
		!isTransient(field) && !isEntityTraitField(field) && !isVarField(field) && !isScalaVariable(field) && !isValidEntityField(field) && field.getType.getSimpleName != "EntityList" && field.getName.split('$').last != "implicitEntity"

	def removeLazyValueValue(fieldsToEnhance: Array[CtField]) = {
		val lazyValueValueSuffix = "Value"
		val lazyValues = fieldsToEnhance.filter((field: CtField) => fieldsToEnhance.filter(_.getName() == field.getName() + lazyValueValueSuffix).nonEmpty)
		fieldsToEnhance.filter((field: CtField) => lazyValues.filter(_.getName() + lazyValueValueSuffix == field.getName()).isEmpty)
	}

	def isEnhanced(clazz: CtClass) =
		clazz.getDeclaredFields.filter(_.getName() == "varTypes").nonEmpty

	def box(typ: CtClass) =
		typ match {
			case typ: CtPrimitiveType =>
				"new " + typ.getWrapperName + "($$)"
			case other =>
				"$$"
		}

	def enhance(clazz: CtClass, classPool: ClassPool): Set[CtClass] = {
		if (!clazz.isInterface() && !clazz.isFrozen && !isEnhanced(clazz) && isEntityClass(clazz, classPool)) {
			var enhancedFieldsMap = Map[CtField, (CtClass, Boolean)]()
			val varClazz = classPool.get(varClassName);
			val allFields = clazz.getDeclaredFields
			val fieldsToEnhance = removeLazyValueValue(allFields.filter(isCandidate))
			for (originalField <- fieldsToEnhance) {
				val name = originalField.getName
				clazz.removeField(originalField)
				val enhancedField = new CtField(varClazz, name, clazz);
				enhancedField.setModifiers(Modifier.PRIVATE)
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
				enhancedFieldsMap += (enhancedField -> originalFieldTypeAndOptionFlag)
			}

			val hashMapClass = classPool.get(hashMapClassName)
			val varTypesField = new CtField(hashMapClass, "varTypes", clazz);
			varTypesField.setModifiers(Modifier.STATIC)
			clazz.addField(varTypesField, "new " + hashMapClassName + "();")

			val init = clazz.makeClassInitializer()

			try
				clazz.instrument(
					new ExprEditor {
						override def edit(fa: FieldAccess) = {
							val field =
								try {
									fa.getField
								} catch {
									case e: javassist.NotFoundException =>
										null
								}
							if (field != null && enhancedFieldsMap.contains(field)) {
								val (typ, optionFlag) = enhancedFieldsMap.get(fa.getField).get
								if (fa.isWriter) {
									if (optionFlag)
										fa.replace("this." + fa.getFieldName + ".put(" + box(typ) + ");")
									else
										fa.replace("this." + fa.getFieldName + ".putValue(" + box(typ) + ");")
								} else if (fa.isReader) {
									if (optionFlag)
										fa.replace("$_ = ($r) this." + fa.getFieldName + ".get($$);")
									else
										fa.replace("$_ = ($r) this." + fa.getFieldName + ".getValue($$);")
								}
							}
						}
					})
			catch {
				case e: javassist.CannotCompileException =>
					val toThrow = new IllegalStateException("Fail to enhance " + clazz.getName)
					toThrow.initCause(e)
					throw toThrow
			}

			for (c <- clazz.getConstructors) {

				var replace =
					"setInitialized();"
				for ((field, (typ, optionFlag)) <- enhancedFieldsMap) {
					if (field.getName == "id")
						replace += "this." + field.getName + " = new " + idVarClassName + "(this);"
					else {
						val isMutable = Modifier.isFinal(field.getModifiers)
						replace += "this." + field.getName + " = new " + varClassName + "(" + isMutable + "," + typ.getName + ".class, \"" + field.getName.split('$').last + "\", this);"
					}
				}
				c.insertBefore(replace)
				c.insertAfter("addToLiveCache();")
			}

			val initBody =
				(for ((field, (typ, optionFlag)) <- enhancedFieldsMap)
					yield "varTypes.put(\"" + field.getName.split('$').last + "\", " + typ.getName + ".class)").mkString(";") + ";"

			init.insertBefore(initBody)

			//			clazz.writeFile
			enhance(clazz.getSuperclass, classPool) + clazz
		} else
			Set()
	}

	def enhance(clazzName: String, classPool: ClassPool): Set[CtClass] = {
		val clazz = classPool.get(clazzName)
		enhance(clazz, classPool)
	}

	def registerDependency(clazz: CtClass, tree: DependencyTree[CtClass], enhancedEntityClasses: Set[CtClass]): Unit = {
		val superClass = clazz.getSuperclass()
		if (superClass != null) {
			if (enhancedEntityClasses.contains(superClass))
				tree.addDependency(superClass, clazz)
			registerDependency(superClass, tree, enhancedEntityClasses)
		}
	}

	private var _enhancedEntityClasses: Option[Set[Class[Entity]]] = None

	def enhancedEntityClasses(referenceClass: Class[_]) = synchronized {
		if (_enhancedEntityClasses.isDefined)
			_enhancedEntityClasses.get
		else {
			verifyNoVerify
			val entityClassNames = Reflection.getAllImplementorsNames(List(classOf[ActivateContext], referenceClass: Class[_]), classOf[Entity])
			var enhancedEntityClasses = Set[CtClass]()
			val classPool = ClassPool.getDefault
			classPool.appendClassPath(new ClassClassPath(this.niceClass))
			for (entityClassName <- entityClassNames)
				enhancedEntityClasses ++= enhance(entityClassName, classPool)
			val tree = new DependencyTree(enhancedEntityClasses)
			for (enhancedEntityClass <- enhancedEntityClasses)
				registerDependency(enhancedEntityClass, tree, enhancedEntityClasses)
			val resolved = tree.resolve
			val res =
				(for (enhancedEntityClass <- resolved)
					yield enhancedEntityClass.toClass.asInstanceOf[Class[Entity]]).toSet
			_enhancedEntityClasses = Some(res)
			res
		}
	}

}
