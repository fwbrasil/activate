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
import net.fwbrasil.activate.util.Reflection._
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
import net.fwbrasil.activate.OptimisticOfflineLocking

object EntityEnhancer extends Logging {

    val varClassName = classOf[Var[_]].getName
    val idVarClassName = classOf[IdVar].getName
    val entityClassName = classOf[BaseEntity].getName
    val entityClassFieldPrefix = entityClassName.replace(".", "$")
    val scalaVariablesPrefixes = Array("$outer", "bitmap$init$")
    val entityValidationFields = Array("listeners", "invariants", "listener")
    val transientBitmapFlagName = "bitmap$"

    def isEntityClass(clazz: CtClass, classPool: ClassPool): Boolean =
        clazz.getInterfaces.contains(classPool.get(entityClassName)) ||
            (clazz.getSuperclass != null && (isEntityClass(clazz.getSuperclass, classPool) || !clazz.getInterfaces.find((interface: CtClass) => isEntityClass(interface, classPool)).isEmpty))

    def isEntityTraitField(field: CtField) =
        field.getName.startsWith(entityClassFieldPrefix)

    def isDisabledVersionVar(field: CtField) =
        field.getName == OptimisticOfflineLocking.versionVarName &&
            !OptimisticOfflineLocking.isEnabled

    def isVarField(field: CtField) =
        field.getType.getName == varClassName

    def isScalaVariable(field: CtField) =
        scalaVariablesPrefixes.filter((name: String) => field.getName.startsWith(name)).nonEmpty

    def isValidEntityField(field: CtField) =
        entityValidationFields.filter((name: String) => field.getName.split("$").last == name).nonEmpty

    def isStatic(field: CtField) =
        Modifier.isStatic(field.getModifiers)

    def isCandidate(field: CtField) =
        !isDisabledVersionVar(field) &&
            !isEntityTraitField(field) &&
            !isVarField(field) &&
            !isScalaVariable(field) &&
            !isValidEntityField(field) &&
            !isStatic(field) &&
            field.getName != "_varsMap" &&
            field.getName != "lastVersionValidation"

    def removeLazyValueValue(fieldsToEnhance: Array[CtField]) = {
        val lazyValueValueSuffix = "Value"
        val lazyValues = fieldsToEnhance.filter((field: CtField) => fieldsToEnhance.filter(_.getName() == field.getName() + lazyValueValueSuffix).nonEmpty)
        fieldsToEnhance.filter((field: CtField) => lazyValues.filter(_.getName() + lazyValueValueSuffix == field.getName()).isEmpty)
    }

    def box(typ: CtClass, ref: String) =
        typ match {
            case typ: CtPrimitiveType =>
                "new " + typ.getWrapperName + "($" + ref + ")"
            case other =>
                "$" + ref
        }

    def enhance(clazz: CtClass, classPool: ClassPool): Set[CtClass] = {
        if (!isLoaded(clazz.getName) && !clazz.isInterface() && !clazz.isFrozen() && isEntityClass(clazz, classPool)) {
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
                injectEntityMetadata(clazz, classPool)
                clazz.freeze
            } catch {
                case e: Throwable =>
                    val toThrow = new IllegalStateException("Fail to enhance " + clazz.getName)
                    toThrow.initCause(e)
                    throw toThrow
            }
            //			clazz.writeFile
            enhance(clazz.getSuperclass, classPool) + clazz
        } else
            Set()
    }

    private def injectEntityMetadata(clazz: CtClass, classPool: ClassPool) = {
        val metadataField = new CtField(classPool.get(classOf[EntityMetadata].getName), "metadata_entity", clazz);
        metadataField.setModifiers(Modifier.STATIC)
        clazz.addField(metadataField)
        val method =
            clazz.getDeclaredMethods.find(_.getName.contains("entityMetadata")).getOrElse {
                val unitClass = classPool.get(classOf[EntityMetadata].getName)
                val method = new CtMethod(unitClass, "entityMetadata", Array(), clazz)
                method.setModifiers(method.getModifiers() & ~Modifier.ABSTRACT)
                clazz.addMethod(method)
                method
            }
        method.setBody("{ return " + clazz.getName + ".metadata_entity; }")
    }

    private def injectBuildVarsMap(clazz: CtClass, enhancedFieldsMap: Map[CtField, (CtField, Boolean)], classPool: ClassPool) = {
        import scala.collection.JavaConversions._
        val init = enhancedFieldsMap.keys.map(field => {
            val ann = field.getAnnotation(classOf[InternalAlias]).asInstanceOf[InternalAlias]
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
                "this._varsMap = new java.util.HashMap();\n"
        method.setBody("{\n" + callSuper + init + "}")

    }

    def enhancedEntityClasses(classpathHints: List[Any]) = synchronized {
        val classPool = new ClassPool(false)
        classPool.appendClassPath(new ClassClassPath(this.getClass))
        val (notLoaded, alreadyLoaded) = entityClassesNames(classpathHints).partition(!isLoaded(_))
        val enhancedEntityClasses =
            notLoaded.map(enhance(_, classPool)).flatten
        val resolved =
            resolveDependencies(enhancedEntityClasses)
        materializeClasses(resolved) ++ getClasses(alreadyLoaded)
    }

    private val findLoadedClassMethod = classOf[ClassLoader].getDeclaredMethod("findLoadedClass", classOf[String])
    findLoadedClassMethod.setAccessible(true)

    private def isLoaded(className: String) = {
        val classLoader = ActivateContext.classLoaderFor(className)
        findLoadedClassMethod.invoke(classLoader, className) != null
    }

    private def enhance(clazzName: String, classPool: ClassPool): Set[CtClass] =
        enhance(classPool.get(clazzName), classPool)

    private def materializeClasses(resolved: List[CtClass]) = {
        import ActivateContext.classLoaderFor
        for (enhancedEntityClass <- resolved) yield 
        enhancedEntityClass.toClass(classLoaderFor(enhancedEntityClass.getName), this.getClass.getProtectionDomain).asInstanceOf[Class[BaseEntity]]
    }

    private def getClasses(names: Set[String]) =
        for (name <- names) yield {
            ActivateContext.loadClass(name).asInstanceOf[Class[BaseEntity]]
        }

    private def entityClassesNames(classpathHints: List[Any]) =
        Reflection.getAllImplementorsNames(classpathHints ++ List(classOf[ActivateContext]), classOf[BaseEntity])

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
                var replace = "setNotInitialized();\nsetInitializing();\nif(this.getClass() == " + clazz.getName + ".class) {beforeConstruct();}\n"
                val initializedFields = fields.map(_.getName)
                for ((field, optionFlag) <- enhancedFieldsMap) {
                    if (field.getName == "id")
                        replace += "this." + field.getName + " = new " + idVarClassName + "(" + clazz.getName + ".metadata_" + field.getName + ", this);\n"
                    else
                        replace += "this." + field.getName + " = new " + varClassName + "(" + clazz.getName + ".metadata_" + field.getName + ", this, " + !initializedFields.contains(field.getName) + ");\n"
                }

                val localsMap = localVariablesMap(codeAttribute)
                for (field <- fields) {
                    def getLocal(name: String) =
                        localsMap.get(name).getOrElse(localsMap(name.split('$').last))
                    val (originalField, optionFlag) = enhancedFieldsMap.get(field).get
                    if (optionFlag)
                        replace += "this." + field.getName + ".putWithoutInitialize(" + box(originalField.getType, getLocal(field.getName).toString) + ");\n"
                    else
                        replace += "this." + field.getName + ".putValueWithoutInitialize(" + box(originalField.getType, getLocal(field.getName).toString) + ");\n"
                }

                c.insertBeforeBody(replace)
                c.insertAfter("if(this.getClass() == " + clazz.getName + ".class) {setInitialized();postConstruct();afterConstruct();}\n")
            }
        }
    }

    private def getFieldName(field: CtField) =
        Option(field.getAnnotation(classOf[InternalAlias]).asInstanceOf[InternalAlias]).map(_.value).getOrElse(field.getName.split('$').last)

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
