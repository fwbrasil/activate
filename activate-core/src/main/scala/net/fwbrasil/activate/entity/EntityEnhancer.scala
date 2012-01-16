package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.Reflection
import javassist.ClassPool
import javassist.CodeConverter
import javassist.CtClass
import javassist.ClassClassPath
import javassist.CtField
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import javassist.expr.FieldAccess
import javassist.expr.ConstructorCall
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.Reflection.set
import net.fwbrasil.activate.util.Reflection.invoke
import javassist.bytecode.LocalVariableAttribute
import javassist.Modifier
import javassist.CtPrimitiveType
import java.lang.management.ManagementFactory
import java.lang.management.RuntimeMXBean
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.util.GraphUtil._

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
		field.getName.startsWith(entityClassFieldPrefix) // || field.getName == "id"

	def isVarField(field: CtField) =
		field.getType.getName == varClassName

	def isScalaVariable(field: CtField) =
		scalaVariables.filter((name: String) => field.getName.startsWith(name)).nonEmpty

	def isValidEntityField(field: CtField) =
		validEntityFields.filter((name: String) => field.getName == name).nonEmpty

	def isCandidate(field: CtField) =
		!isEntityTraitField(field) && !isVarField(field) && !isScalaVariable(field) && !isValidEntityField(field)

	def removeLazyValueValue(fieldsToEnhance: Array[CtField]) = {
		val lazyValueValueSuffix = "Value"
		val lazyValues = fieldsToEnhance.filter((field: CtField) => fieldsToEnhance.filter(_.getName() == field.getName() + lazyValueValueSuffix).nonEmpty)
		fieldsToEnhance.filter((field: CtField) => lazyValues.filter(_.getName() + lazyValueValueSuffix == field.getName()).isEmpty)
	}

	def isEnhanced(clazz: CtClass) =
		clazz.getDeclaredFields.filter(_.getName() == "varTypes").nonEmpty

	def box(typ: CtClass) =
		if (typ.isPrimitive) {
			val ctPrimitive = typ.asInstanceOf[CtPrimitiveType]
			"new " + ctPrimitive.getWrapperName + "($$)"
		} else
			"$$"

	def enhance(clazz: CtClass, classPool: ClassPool): Set[CtClass] = {
		if (!clazz.isInterface() && !clazz.isFrozen && !isEnhanced(clazz) && isEntityClass(clazz, classPool)) {
			var enhancedFieldsMap = Map[CtField, CtClass]()
			val varClazz = classPool.get(varClassName);
			val allFields = clazz.getDeclaredFields
			val fieldsToEnhance = removeLazyValueValue(allFields.filter((field: CtField) => isCandidate(field)))
			for (originalField <- fieldsToEnhance; if (isCandidate(originalField))) {
				val name = originalField.getName
				clazz.removeField(originalField)
				val enhancedField = new CtField(varClazz, name, clazz);
				enhancedField.setModifiers(Modifier.PRIVATE)
				clazz.addField(enhancedField)
				val originalFieldType = originalField.getType
				enhancedFieldsMap += (enhancedField -> originalFieldType)
			}

			val hashMapClass = classPool.get(hashMapClassName)
			val varTypesField = new CtField(hashMapClass, "varTypes", clazz);
			varTypesField.setModifiers(Modifier.STATIC)
			clazz.addField(varTypesField, "new " + hashMapClassName + "();")

			val init = clazz.makeClassInitializer()

			clazz.instrument(
				new ExprEditor {
					override def edit(fa: FieldAccess) = {
						if (enhancedFieldsMap.contains(fa.getField)) {
							if (fa.isWriter) {
								val typ = enhancedFieldsMap.get(fa.getField).get
								fa.replace("this." + fa.getFieldName + ".$colon$eq(" + box(typ) + ");")
							} else if (fa.isReader) {
								fa.replace("$_ = ($r) this." + fa.getFieldName + ".unary_$bang($$);")
							}
						}
					}
				})

			for (c <- clazz.getConstructors) {
				var replace = ""
				for ((field, typ) <- enhancedFieldsMap) {
					if (field.getName == "id")
						replace += "this." + field.getName + " = new " + idVarClassName + "(this);"
					else
						replace += "this." + field.getName + " = new " + varClassName + "(" + typ.getName + ".class, \"" + field.getName + "\", this);"
				}
				c.insertBefore(replace)
				c.insertAfter("addToLiveCache();")
				c.insertAfter("validate();")
			}

			val initBody =
				(for ((field, typ) <- enhancedFieldsMap)
					yield "varTypes.put(\"" + field.getName + "\", " + typ.getName + ".class)").mkString(";") + ";"

			init.insertBefore(initBody)

			//			clazz.writeFile;
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

	lazy val enhancedEntityClasses = {
		verifyNoVerify
		val entityClassNames = Reflection.getAllImplementorsNames(classOf[Entity].getName)
		var enhancedEntityClasses = Set[CtClass]()
		val classPool = ClassPool.getDefault
		classPool.appendClassPath(new ClassClassPath(this.getClass()))
		for (entityClassName <- entityClassNames)
			enhancedEntityClasses ++= enhance(entityClassName, classPool)
		val tree = new DependencyTree(enhancedEntityClasses)
		for (enhancedEntityClass <- enhancedEntityClasses)
			registerDependency(enhancedEntityClass, tree, enhancedEntityClasses)
		val resolved = tree.resolve
		for (enhancedEntityClass <- resolved)
			yield enhancedEntityClass.toClass.asInstanceOf[Class[Entity]]
	}

}
