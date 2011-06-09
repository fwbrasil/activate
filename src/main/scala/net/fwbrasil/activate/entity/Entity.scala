package net.fwbrasil.activate.entity

import java.lang.reflect.{Modifier, Field}
import net.fwbrasil.activate.cache.live._
import net.fwbrasil.activate.util.uuid.Idable
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionContext
import scala.collection._

trait Entity extends Idable {
  println("A ConstructionCode")

  def delayedInit(body: => Unit) = {
    body
    boundVarsToEntity
    addToLiveCache
  }
  
  def delete = {
		initialize
		for (ref <- vars)
			ref.destroy
	}

	def isDeleted =
		vars.first.isDestroyed

	private[this] var persistedflag = false
	private[this] var isVarsBound = false
	private[this] var initialized = true

	private[activate] def setPersisted =
		persistedflag = true

	private[activate] def isPersisted =
		persistedflag

	private[activate] def setNotInitialized =
		initialized = false

	private[activate] def isInitialized =
		initialized

	private[activate] def initialize =
		this.synchronized {
			if (!initialized)
				context.initialize(this.asInstanceOf[Entity])
			initialized = true
		}

	private[activate] def isInLiveCache =
		context.liveCache.contains(this.asInstanceOf[Entity])

	private[this] def varFields =
		EntityHelper.getEntityFields(this.getClass.asInstanceOf[Class[Entity]])._2

	private[activate] def idField =
		EntityHelper.getEntityFields(this.getClass.asInstanceOf[Class[Entity]])._1

	@transient
	private[this] var varFieldsMapCache: Map[String, Var[_]] = _

	private[this] def buildVarFieldsMap =
		(for ((varField, typ) <- varFields; ref = varField.get(this).asInstanceOf[Var[Any]]; if (ref != null))
			yield if (ref.name == null)
			throw new IllegalStateException("Ref should have a name!")
		else
			(ref.name -> ref)).toMap

	private[this] def varFieldsMap = {
		if (varFieldsMapCache == null) {
			varFieldsMapCache = buildVarFieldsMap
		}
		varFieldsMapCache
	}

	private[activate] def vars =
		varFieldsMap.values

	private[this] def context: ActivateContext = {
		val (field, typ) = varFields(0)
		val value = field.get(this)
		value.asInstanceOf[Var[_]].context
	}

	private[activate] def varNamed(name: String) =
		varFieldsMap.get(name)

	
	private[activate] def boundVarsToEntity = {
		isVarsBound.asInstanceOf[AnyRef].synchronized {
			if (!isVarsBound) {
				for ((field, typ) <- varFields) {
					val entityVar = field.get(this)
					if (entityVar != null) {
						val castVar = entityVar.asInstanceOf[Var[_]]
						castVar.outerEntity = this.asInstanceOf[Entity]
						castVar.name = field.getName.split('$').last
					}
				}
				isVarsBound = true
			}
		}
	}

	private[activate] def addToLiveCache =
		context.liveCache.toCache(this.asInstanceOf[Entity])

	private[activate] def cachedInstance =
		context.liveCache.cachedInstance(this.asInstanceOf[Entity])

	override def equals(other: Any) =
		other match {
			case entity: Entity =>
				this.id == entity.id
			case other =>
				false
		}

	override def hashCode =
		this.id.hashCode

	override def toString =
		this.getClass.getSimpleName + "(" + id + ")"

}

object EntityHelper {

	private[this] val entityVarFields =
		mutable.WeakHashMap[Class[Entity], (Field, List[(Field, Class[_])])]()

	private[activate] def getEntityFields(clazz: Class[Entity]) =
		entityVarFields.getOrElseUpdate(clazz, {
			lazy val scalaSig = Reflection.getScalaSig(clazz)
			val allFields = Reflection.getDeclaredFieldsIncludingSuperClasses(clazz)
			val varFields = allFields.filter(_.getType.isAssignableFrom(classOf[Var[_]]))
			val idField = allFields.filter(_.getName.equals("id")).head
			idField.setAccessible(true)
			if (varFields.isEmpty)
				throw new IllegalStateException("An entity must have at least one var.")
			for (field <- varFields)
				if (!Modifier.isFinal(field.getModifiers))
					throw new IllegalStateException("Var fields must be val.")
			varFields.foreach(_.setAccessible(true))
			(idField, for (field <- varFields)
				yield (field, Reflection.getEntityFieldTypeArgument(scalaSig, field)))
		})

}

trait EntityContext extends ValueContext with TransactionContext {

	private[activate] val liveCache: LiveCache

	type Entity = net.fwbrasil.activate.entity.Entity

	type Var[A] = net.fwbrasil.activate.entity.Var[A]

	def initialize(entity: Entity)

	implicit def varToValue[A](ref: Var[A]): A =
		if (ref == null)
			null.asInstanceOf[A]
		else !ref

}