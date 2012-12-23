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
import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import javassist.CannotCompileException
import javassist.bytecode.AnnotationsAttribute
import javassist.CtMethod

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

	def isStatic(field: CtField) =
		Modifier.isStatic(field.getModifiers)

	def isCandidate(field: CtField) =
		!isEntityTraitField(field) && !isVarField(field) && !isScalaVariable(field) && !isValidEntityField(field) && !isStatic(field) && field.getName != "_varsMap"

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
				injectBuildVarsMap(clazz, enhancedFieldsMap, classPool)
				clazz.freeze
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

	private def injectBuildVarsMap(clazz: CtClass, enhancedFieldsMap: Map[CtField, (CtField, Boolean)], classPool: ClassPool) = {
		import scala.collection.JavaConversions._
		val init = enhancedFieldsMap.keys.map(field => {
			val ann = field.getAnnotation(classOf[Alias]).asInstanceOf[Alias]
			val fieldName =
				if (ann != null)
					ann.value
				else
					field.getName
			"this.putVar(\"" + fieldName + "\", this." + field.getName() + ");"
		}).mkString("\n")
		val method =
			clazz.getDeclaredMethods.find(_.getName.contains("buildVarsMap")).getOrElse {
				val unitClass = classPool.get(classOf[Unit].getName)
				val method = new CtMethod(unitClass, "buildVarsMap", Array(), clazz)
				method.setModifiers(method.getModifiers() & ~Modifier.ABSTRACT)
				clazz.addMethod(method)
				method
			}
		val callSuper =
			if (clazz.getSuperclass.getDeclaredMethods.find(_.getName.contains("buildVarsMap")).nonEmpty)
				"super.buildVarsMap();\n"
			else
				""
		method.setBody("{" + callSuper + init + "}")

	}

	def enhancedEntityClasses(referenceClass: Class[_]) = synchronized {
		val classPool = buildClassPool
		val enhancedEntityClasses =
			entityClassesNames(referenceClass)
				.map(enhance(_, classPool)).flatten
		val resolved = resolveDependencies(enhancedEntityClasses)
		materializeClasses(resolved)
	}

	private def enhance(clazzName: String, classPool: ClassPool): Set[CtClass] =
		enhance(classPool.get(clazzName), classPool)

	private def materializeClasses(resolved: List[CtClass]) = {
		import ActivateContext.classLoaderFor
		for (enhancedEntityClass <- resolved) yield try
			enhancedEntityClass.toClass(classLoaderFor(enhancedEntityClass.getName)).asInstanceOf[Class[Entity]]
		catch {
			case e: CannotCompileException =>
				classLoaderFor(enhancedEntityClass.getName).loadClass(enhancedEntityClass.getName).asInstanceOf[Class[Entity]]
		}
	}

	private def entityClassesNames(referenceClass: Class[_]) =
		Reflection.getAllImplementorsNames(List(classOf[ActivateContext], referenceClass: Class[_]), classOf[Entity])

	private def buildClassPool = {
		val classPool = new ClassPool(false)
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
		val enhancedField = new CtField(varClazz, name, clazz)
		enhancedField.setModifiers(originalField.getModifiers)
		val fieldInfo = originalField.getFieldInfo
		val originalAttribute = fieldInfo.getAttribute(AnnotationsAttribute.visibleTag).asInstanceOf[AnnotationsAttribute]
		if (originalAttribute != null)
			enhancedField.getFieldInfo.addAttribute(originalAttribute.copy(enhancedField.getFieldInfo.getConstPool(), null))
		clazz.addField(enhancedField)
		val optionFlag = originalField.getType.getName == classOf[Option[_]].getName
		val entityPropertyMetadataClass = classPool.get(classOf[EntityPropertyMetadata].getName)
		val metadataField = new CtField(entityPropertyMetadataClass, "metadata_" + name, clazz);
		metadataField.setModifiers(Modifier.STATIC)
		clazz.addField(metadataField)

		(enhancedField, (originalField, optionFlag))
	}

	private def enhanceConstructors(clazz: CtClass, enhancedFieldsMap: Map[CtField, (CtField, Boolean)]) = {
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
							val (originalField, optionFlag) = enhancedFieldsMap.get(fa.getField).get
							enhanceFieldAccess(fa, originalField, optionFlag)
						}
					}
				}
			})
			if (isPrimaryConstructor) {
				var replace = "setInitialized();\n"
				for ((field, optionFlag) <- enhancedFieldsMap) {
					if (field.getName == "id")
						replace += "this." + field.getName + " = new " + idVarClassName + "(" + clazz.getName + ".metadata_" + field.getName + ", this);\n"
					else
						replace += "this." + field.getName + " = new " + varClassName + "(" + clazz.getName + ".metadata_" + field.getName + ", this);\n"
				}

				val localsMap = localVariablesMap(codeAttribute)
				for (field <- fields) {
					val (originalField, optionFlag) = enhancedFieldsMap.get(field).get
					if (optionFlag)
						replace += "this." + field.getName + ".put(" + box(originalField.getType, localsMap(field.getName).toString) + ");\n"
					else
						replace += "this." + field.getName + ".putValue(" + box(originalField.getType, localsMap(field.getName).toString) + ");\n"
				}

				c.insertBeforeBody(replace)
				c.insertAfter("if(this.getClass() == " + clazz.getName + ".class) {validateOnCreate();addToLiveCache();}\n")
			}
		}
	}

	private def getFieldName(field: CtField) =
		Option(field.getAnnotation(classOf[Alias]).asInstanceOf[Alias]).map(_.value).getOrElse(field.getName.split('$').last)

	private def enhanceFieldsAccesses(clazz: javassist.CtClass, enhancedFieldsMap: scala.collection.immutable.Map[javassist.CtField, (CtField, Boolean)]): Unit = {

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
							val (originalField, optionFlag) = enhancedFieldsMap.get(fa.getField).get
							enhanceFieldAccess(fa, originalField, optionFlag)
						}
					}
				}

			})
	}

	private def enhanceFieldAccess(fa: FieldAccess, originalField: CtField, optionFlag: Boolean) =
		if (fa.isWriter) {
			if (optionFlag)
				fa.replace("this." + fa.getFieldName + ".put(" + box(originalField.getType, "$") + ");")
			else
				fa.replace("this." + fa.getFieldName + ".putValue(" + box(originalField.getType, "$") + ");")
		} else if (fa.isReader) {
			if (optionFlag)
				fa.replace("$_ = ($r) this." + fa.getFieldName + ".get($$);")
			else
				fa.replace("$_ = ($r) this." + fa.getFieldName + ".getValue($$);")
		}

}
