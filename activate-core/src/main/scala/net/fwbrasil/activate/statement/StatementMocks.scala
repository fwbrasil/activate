package net.fwbrasil.activate.statement

import net.fwbrasil.activate.util.Reflection._
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
import net.fwbrasil.activate.entity.EntityPropertyMetadata

object StatementMocks {

    val entityMockCache = MutableMap[Class[_ <: Entity], Entity]()

    var _lastFakeVarCalled =
        new ThreadLocal[Stack[FakeVar[_]]] {
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

    class FakeVar[P](metadata: EntityPropertyMetadata, outerEntity: Entity, val _outerEntityClass: Class[Entity], val originVar: FakeVar[_])
            extends Var[P](metadata, outerEntity, false) {

        override def outerEntityClass = _outerEntityClass

        override def get: Option[P] = {
            val value =
                if (classOf[Entity].isAssignableFrom(valueClass))
                    mockEntity(valueClass.asInstanceOf[Class[Entity]], this)
                else
                    mock(valueClass)
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
            case "char"               => 'M'
            case "int"                => 0
            case "long"               => 0l
            case "float"              => 0f
            case "double"             => 0d
            case "boolean"            => false
            case "java.util.Calendar" => java.util.Calendar.getInstance
            case "java.lang.String"   => "mock"
            case "[B"                 => Array[Byte]()
            case other =>
                null
        }
    }

    def mockEntity[E <: Entity](clazz: Class[E]): E =
        entityMockCache.getOrElseUpdate(clazz, mockEntityWithoutCache[E](clazz)).asInstanceOf[E]

    def mockEntityWithoutCache[E <: Entity](clazz: Class[E]): E =
        mockEntity[E](clazz, null).asInstanceOf[E]

    def mockEntity[E <: Entity](entityClass: Class[E], originVar: FakeVar[_]): E = {
        val concreteClass = EntityHelper.concreteClasses(entityClass).headOption.getOrElse {
            throw new IllegalStateException(
                "Can't find a concrete class for " + entityClass + ".\n" +
                    "Maybe the context isn't initialized or you must override acceptEntity on your context.\n" +
                    "Important: The context definition must be declared in a base package of the entities packages.\n" +
                    "Example: com.app.myContext for com.app.model.MyEntity")
        }
        val entity = newInstance(concreteClass)
        val entityMetadata = entity.entityMetadata
        val context = ActivateContext.contextFor(entityClass)
        for (propertyMetadata <- entityMetadata.propertiesMetadata)
            context.transactional(context.transient) {
                val ref = new FakeVar[Any](propertyMetadata, entity, entityClass.asInstanceOf[Class[Entity]], originVar)
                val field = propertyMetadata.varField
                field.set(entity, ref)
            }
        entity.buildVarsMap
        entity.asInstanceOf[E]
    }

    def mockVar =
        newInstanceUnitialized(classOf[FakeVar[_]])
}