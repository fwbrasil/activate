package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.Reflection.set
import net.fwbrasil.activate.util.uuid.UUIDUtil.generateUUID
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionContext
import scala.collection.mutable.{Map => MutableMap}

object QueryMocks {
	
	
	val entityMockCache = MutableMap[Class[_ <: Entity], Entity]()
	
	var _lastFakeVarCalled: Option[Var[Any]] = None
	
	def lastFakeVarCalled = {
		val last = _lastFakeVarCalled
		clearFakeVarCalled
		last
	}
	
	def clearFakeVarCalled = {
		_lastFakeVarCalled = None
	}
	
    class FakeVarToQuery[P]
		extends 
			Var[P](null, null, null) {
    	
    	def entityValueMock = 
    			(EntityValue.tvalFunction(fakeValueClass))(None).asInstanceOf[EntityValue[P]]
        var fakeValueClass: Class[_] = _
        var originVar: FakeVarToQuery[_] = _
        override def get: Option[P] = {
        	val value =
                if (classOf[Entity].isAssignableFrom(fakeValueClass))
                    mockEntity(fakeValueClass.asInstanceOf[Class[Entity]], this)
                else
                    if(!fakeValueClass.isPrimitive())
                    	newInstance(fakeValueClass)
                	else
                    	mockPrimitive(fakeValueClass)	
            _lastFakeVarCalled = Some(this.asInstanceOf[Var[Any]])
            Option(value.asInstanceOf[P])
        }
        override def put(value: Option[P]): Unit = {
    		throw new IllegalStateException("You can't alter vars in a predicate!")
        }
    }
    
    def mockPrimitive(clazz: Class[_]) = {
    	println(clazz)
    	clazz.getName match {
			case "char" => 'M'
			case "integer" => 0
			case "long" => 0l
			case "float" => 0f
			case "double" => 0d
			case "boolean" => false
			case other =>
				throw new IllegalStateException("Invalid primitive type "+ other)
    	}
    }

    def mockEntity[E <: Entity](clazz: Class[E]): E =
    	entityMockCache.getOrElseUpdate(clazz, mockEntity[E](clazz, null)).asInstanceOf[E]
    
    def mockEntity[E <: Entity](entityClass: Class[E], originVar: FakeVarToQuery[_]): E = {
        val entity = newInstance(entityClass).asInstanceOf[E]
        val entityMetadata = EntityHelper.getEntityMetadata(entityClass)
        for (propertyMetadata <- entityMetadata.propertiesMetadata) {
            val ref = mockVar
            val typ = propertyMetadata.propertyType
            val field = propertyMetadata.varField
            ref.fakeValueClass = typ
            ref.originVar = originVar
            set(ref, "name", field.getName())
            set(ref, "outerEntity", entity)
            field.set(entity, ref)
        }
        entityMetadata.idField.set(entity, generateUUID)
        entity
    }

    def mockVar = 
    	newInstance(classOf[FakeVarToQuery[_]]).asInstanceOf[FakeVarToQuery[_]]

}