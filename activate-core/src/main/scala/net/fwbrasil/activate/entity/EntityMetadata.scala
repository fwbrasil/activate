package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.Reflection._
import language.existentials
import net.fwbrasil.activate.util.Reflection
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import net.fwbrasil.smirror._
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.activate.entity.id.EntityId

class EntityPropertyMetadata(
        val entityMetadata: EntityMetadata,
        val varField: Field,
        entityMethods: List[Method],
        entityClass: Class[Entity]) {
    val javaName = varField.getName
    val originalName = javaName.split('$').last
    val name =
        Option(varField.getAnnotation(classOf[InternalAlias]))
            .map(_.value)
            .getOrElse(originalName)
    def findMethods(names: String*) =
        entityMethods.filter(m => names.contains(m.getName))
    val getter = findMethods(javaName, originalName).filter(_.getParameterTypes.isEmpty).headOption.getOrElse {
        if (name.head.isDigit)
            null
        else
            throw new IllegalStateException(s"Can't find getter for $entityClass.$name. Maybe it should be declared as var or val.")
    }
    val setter = findMethods(javaName + "_$eq", originalName + "_$eq").headOption.orNull
    val genericParameter = {
        if (getter == null)
            classOf[Object]
        else
            getter.getGenericReturnType match {
                case typ: ParameterizedType =>
                    val initial = typ.getActualTypeArguments.headOption.map(_ match {
                        case typ: ParameterizedType =>
                            typ.getRawType.asInstanceOf[Class[_]]
                        case clazz: Class[_] =>
                            clazz
                        case other =>
                            classOf[Object]
                    }).getOrElse(classOf[Object])
                    if (initial == classOf[Object]) {
                        val fields = entityMetadata.sClass.fields
                        val fieldOption = fields.find(_.name == originalName)
                        val res = fieldOption.flatMap(_.typeArguments.headOption)
                        res.flatMap(_.javaClassOption).getOrElse(classOf[Object])
                    } else
                        initial
                case other =>
                    classOf[Object]
            }
    }
    val propertyType =
        if (name == "id")
            EntityId.idClassFor(entityClass)
        else {
            val typ =
                if (getter == null)
                    classOf[Object]
                else
                    getter.getReturnType
            if (typ == classOf[Option[_]])
                genericParameter
            else if (typ == classOf[Object]) {
                val fields = entityMetadata.sClass.fields
                val fieldOption = fields.find(_.name == originalName)
                fieldOption.flatMap(_.sClass.javaClassOption).getOrElse(classOf[Object])
            } else
                typ
        }
    require(propertyType != null)
    if (propertyType == classOf[Enumeration#Value])
        throw new IllegalArgumentException("To use enumerations with activate you must sublcass Val. " +
            "Instead of \"type MyEnum = Value\", use " +
            "\"case class MyEnum(name: String) extends Val(name)\"")
    val isMutable =
        setter != null
    val isTransient =
        Modifier.isTransient(varField.getModifiers)
    val isOption =
        getter != null && getter.getReturnType == classOf[Option[_]]
    val isLazyFlag =
        javaName.startsWith("bitmap$")
    val tval =
        if (isTransient || isLazyFlag)
            null
        else
            EntityValue.tvalFunctionOption[Any](propertyType, genericParameter)
                .getOrElse(throw new IllegalStateException("Invalid entity property type. " + entityMetadata.name + "." + name + ": " + propertyType))
    varField.setAccessible(true)
    Option(getter).map(_.setAccessible(true))

    if (varField.getDeclaringClass == entityMetadata.entityClass) {
        val metadataField = entityMetadata.entityClass.getDeclaredField("metadata_" + varField.getName)
        metadataField.setAccessible(true)
        metadataField.set(entityMetadata.entityClass, this)
    }

    override def toString = "Property: " + name

}

object EntityPropertyMetadata {

    def nestedListNamesFor(metadata: EntityMetadata, property: EntityPropertyMetadata): (String, String) =
        if (property.originalName != property.name)
            (property.name, property.name)
        else
            (property.name, this.listTableName(metadata.name, property.name))

    def nestedListNamesFor(entityClass: Class[_], propertyName: String): (String, String) = {
        val metadata =
            EntityHelper
                .metadatas
                .find(_.entityClass == entityClass)
                .get
        val property =
            metadata
                .propertiesMetadata
                .find(_.name == propertyName).get
        nestedListNamesFor(metadata, property)
    }

    def listTableName(ownerTableName: String, listName: String) =
        ownerTableName + listName.capitalize
}

class EntityMetadata(
        val name: String,
        val entityClass: Class[Entity]) {
    val allFields =
        Reflection.getDeclaredFieldsIncludingSuperClasses(entityClass)
    val allMethods =
        Reflection.getDeclaredMethodsIncludingSuperClasses(entityClass)
    val invariantMethods =
        allMethods.filter((method: Method) =>
            method.getReturnType == classOf[Invariant[_]]
                && method.getName != "invariant"
                && method.getParameterTypes.isEmpty)
    val listenerMethods =
        allMethods.filter((method: Method) =>
            method.getReturnType == classOf[RefListener[_]]
                && method.getParameterTypes.isEmpty)
    val varFields =
        allFields.filter(
            (field: Field) =>
                classOf[Var[_]].isAssignableFrom(field.getType)
                    && field.getName != "net$fwbrasil$activate$entity$Entity$$_baseVar")
    val idField =
        allFields.find(_.getName == "id").get
    def isEntityProperty(varField: Field) =
        varField.getName.split('$').last != "_baseVar"
    val propertiesMetadata =
        (for (varField <- varFields; if (isEntityProperty(varField)))
            yield new EntityPropertyMetadata(this, varField, allMethods, entityClass)).sortBy(_.name)
    val persistentPropertiesMetadata =
        propertiesMetadata.filter(p => !p.isTransient && !p.isLazyFlag)
    val persistentListPropertiesMetadata =
        persistentPropertiesMetadata.filter(p => p.propertyType == classOf[List[_]] || p.propertyType == classOf[LazyList[_]])
    val idPropertyMetadata =
        propertiesMetadata.find(_.name == "id").getOrElse(
            throw new IllegalStateException(
                "Can't find the entity 'id' property, probably the entity class was loaded before the persistence context. " +
                    "Try to add a 'transactional{}' call on the application startup to force the persistence context load."))
    allMethods.foreach(_.setAccessible(true))
    allFields.foreach(_.setAccessible(true))
    lazy val sClass = sClassOf(entityClass)(runtimeMirror(entityClass.getClassLoader))

    val metadataField = entityClass.getDeclaredField("metadata_entity")
    metadataField.setAccessible(true)
    metadataField.set(entityClass, this)

    lazy val references =
        EntityHelper.metadatas
            .filter(_.entityClass.isConcreteClass)
            .map(m => (m, m.persistentPropertiesMetadata.filter(_.propertyType.isAssignableFrom(this.entityClass))))
            .filter(_._2.nonEmpty).toMap

    override def toString = "Entity metadata for " + name
}