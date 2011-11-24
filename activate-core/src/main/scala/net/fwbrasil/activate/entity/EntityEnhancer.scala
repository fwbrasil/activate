package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.Reflection
import javassist.ClassPool
import javassist.CodeConverter
import javassist.CtClass
import javassist.CtField
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import javassist.expr.FieldAccess
import javassist.expr.ConstructorCall
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.util.Reflection.set
import net.fwbrasil.activate.util.Reflection.invoke
import javassist.ClassClassPath
import javassist.bytecode.LocalVariableAttribute
import javassist.Modifier
import javassist.CtPrimitiveType

object EntityEnhancer {

	val varClassName = classOf[Var[_]].getName()
	val entityClassName = classOf[Entity].getName()
	val entityClassFieldPrefix = entityClassName.replace(".", "$")

	def isEntityClass(clazz: CtClass, classPool: ClassPool): Boolean =
		clazz.getInterfaces.contains(classPool.get(entityClassName)) ||
			(clazz.getSuperclass() != null && (isEntityClass(clazz.getSuperclass(), classPool) || !clazz.getInterfaces().find((interface: CtClass) => isEntityClass(interface, classPool)).isEmpty))

	def isEntityTraitField(field: CtField) =
		field.getName().startsWith(entityClassFieldPrefix) || field.getName() == "id"

	def isVarField(field: CtField) =
		field.getType().getName() == varClassName

	def box(typ: CtClass) =
		if (typ.isPrimitive()) {
			val ctPrimitive = typ.asInstanceOf[CtPrimitiveType]
			"new " + ctPrimitive.getWrapperName + "($$)"
		} else
			"$$"

	def enhance(clazz: CtClass, classPool: ClassPool): Set[CtClass] = {
		if (!clazz.isFrozen() && isEntityClass(clazz, classPool)) {
			var enhancedFieldsMap = Map[CtField, CtClass]()
			val varClazz = classPool.get(varClassName);
			for (originalField <- clazz.getDeclaredFields; if (!isEntityTraitField(originalField) && !isVarField(originalField))) {
				val name = originalField.getName()
				clazz.removeField(originalField)
				val enhancedField = new CtField(varClazz, name, clazz);
				enhancedField.setModifiers(Modifier.PRIVATE)
				clazz.addField(enhancedField)
				enhancedFieldsMap += (enhancedField -> originalField.getType())
			}
			
			clazz.instrument(
				new ExprEditor() {
					override def edit(fa: FieldAccess) = {
						if (enhancedFieldsMap.contains(fa.getField())) {
							if (fa.isWriter()) {
								val typ = enhancedFieldsMap.get(fa.getField()).get
								fa.replace("this." + fa.getFieldName() + ".$colon$eq(" + box(typ) + ");")
							} else if (fa.isReader()) {
								fa.replace("$_ = ($r) this." + fa.getFieldName() + ".unary_$bang($$);")
							}
						}
					}
				})
			for (c <- clazz.getConstructors()) {
				var replace = ""
				for ((field, typ) <- enhancedFieldsMap)
					replace += "this." + field.getName() + " = new " + varClassName + "(" + typ.getName + ".class, \"" + field.getName() + "\", this);"
				c.insertBefore(replace)
				c.insertAfter("addToLiveCache();")
			}

			clazz.writeFile();
			enhance(clazz.getSuperclass(), classPool) + clazz
		} else
			Set()
	}

	def enhance(clazzName: String): Set[CtClass] = {
		val classPool = ClassPool.getDefault();
		val clazz = classPool.get(clazzName)
		if(clazz.isFrozen()) {
			println(clazz)
		}
		enhance(clazz, classPool)
	}
	
	def enhancedEntityClasses = {
		val entityClassNames = Reflection.getAllImplementorsNames(classOf[Entity].getName())
		var enhancedEntityClasses = Set[CtClass]()
		for(entityClassName <- entityClassNames)
			enhancedEntityClasses ++= enhance(entityClassName)
		for(enhancedEntityClass <- enhancedEntityClasses)
			yield enhancedEntityClass.toClass().asInstanceOf[Class[Entity]]
	}

}
