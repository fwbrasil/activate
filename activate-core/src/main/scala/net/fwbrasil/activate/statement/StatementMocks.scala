package net.fwbrasil.activate.statement

import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.Reflection.materializeJodaInstant
import net.fwbrasil.activate.util.Reflection.set
import net.fwbrasil.activate.util.Reflection.get
import net.fwbrasil.activate.util.uuid.UUIDUtil.generateUUID
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionContext
import scala.collection.mutable.{ Map => MutableMap }
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.entity.IdVar
import net.fwbrasil.activate.util.uuid.UUIDUtil
import org.joda.time.base.AbstractInstant
import java.util.Date
import scala.collection.mutable.Stack

object StatementMocks {

	val entityMockCache = MutableMap[Class[_ <: Entity], Entity]()

	var _lastFakeVarCalled =
		new ThreadLocal[Stack[Var[_]]] {
			override def initialValue = Stack()
		}

	def lastFakeVarCalled = {
		val last = _lastFakeVarCalled.get.headOption
		clearFakeVarCalled
		last
	}

	def fakeVarCalledStack = {
		val stack = _lastFakeVarCalled.get.toSeq
		clearFakeVarCalled
		stack
	}

	def clearFakeVarCalled =
		_lastFakeVarCalled.set(Stack())

	class FakeVar[P]
			extends Var[P](None, true, null, null, null) {

		override lazy val tval = EntityValue.tvalFunction[P](fakeValueClass)
		def entityValueMock =
			(EntityValue.tvalFunction(fakeValueClass))(None).asInstanceOf[EntityValue[P]]
		var fakeValueClass: Class[_] = _
		var originVar: FakeVar[_] = _
		var fakeOuterEntityClass: Class[_] = _
		override def outerEntityClass = fakeOuterEntityClass.asInstanceOf[Class[Entity]]
		override def get: Option[P] = {
			val value =
				if (classOf[Entity].isAssignableFrom(fakeValueClass))
					mockEntity(fakeValueClass.asInstanceOf[Class[Entity]], this)
				else
					mock(fakeValueClass)
			_lastFakeVarCalled.get.push(this)
			Option(value.asInstanceOf[P])
		}
		override def put(value: Option[P]): Unit = {
			throw new IllegalStateException("You can't alter vars in a predicate!")
		}
		override def toString =
			name
	}

	def mock(clazz: Class[_]) = {
		clazz.getName match {
			case "char" => 'M'
			case "int" => 0
			case "long" => 0l
			case "float" => 0f
			case "double" => 0d
			case "boolean" => false
			case "java.util.Calendar" => java.util.Calendar.getInstance
			case "java.lang.String" => "mock"
			case "[B" => Array[Byte]()
			case other =>
				if (classOf[Enumeration#Value].isAssignableFrom(clazz)) {
					null
				} else if (classOf[AbstractInstant].isAssignableFrom(clazz)) {
					materializeJodaInstant(clazz, new Date)
				} else newInstance(clazz)
		}
	}

	def mockEntity[E <: Entity](clazz: Class[E]): E =
		entityMockCache.getOrElseUpdate(clazz, mockEntityWithoutCache[E](clazz)).asInstanceOf[E]

	def mockEntityWithoutCache[E <: Entity](clazz: Class[E]): E =
		mockEntity[E](clazz, null).asInstanceOf[E]

	def mockEntity[E <: Entity](entityClass: Class[E], originVar: FakeVar[_]): E = {
		val concreteClass = EntityHelper.concreteClasses(entityClass).head
		val entity = newInstance(concreteClass)
		val entityMetadata = EntityHelper.getEntityMetadata(concreteClass)
		for (propertyMetadata <- entityMetadata.propertiesMetadata) {
			val ref = mockVar
			val typ = propertyMetadata.propertyType
			val field = propertyMetadata.varField
			ref.fakeValueClass = typ
			ref.fakeOuterEntityClass = entityClass
			ref.originVar = originVar
			set(ref, "name", field.getName())
			set(ref, "outerEntity", entity)
			field.set(entity, ref)
		}
		val idField = entityMetadata.idField
		val ref = mockVar
		ref.fakeValueClass = classOf[String]
		ref.fakeOuterEntityClass = entityClass
		ref.originVar = originVar
		set(ref, "name", "id")
		set(ref, "outerEntity", entity)
		idField.set(entity, ref)
		entity
	}

	def mockVar =
		newInstance(classOf[FakeVar[_]])
}