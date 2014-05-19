package net.fwbrasil.activate.entity

import java.lang.reflect.Field
import java.lang.reflect.Method
import scala.language.existentials
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.Reflection.toRichClass
import net.fwbrasil.smirror.runtimeMirror
import net.fwbrasil.smirror.sClassOf
import net.fwbrasil.radon.ref.RefListener

class EntityMetadata(
    val name: String,
    val entityClass: Class[BaseEntity]) {
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
                    && field.getName != "net$fwbrasil$activate$entity$BaseEntity$$_baseVar")
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
                s"Can't find the entity 'id' property for $entityClass, probably the entity class was loaded before the persistence context. " +
                    "Try to add a 'transactional{}' call on the application startup to force the persistence context load." + 
                    "Please also make sure that there aren't instance variables or methods referencing entities inside the main class."))
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

    override def toString = "BaseEntity metadata for " + name
}