package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.uuid.UUIDUtil.generateUUID
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.radon.transaction.TransactionContext
import scala.collection.mutable.{Map => MutableMap}

object QueryMocks {
	
	
	val entityMockCache = MutableMap[Class[_ <: Entity], Entity]()
	
    class FakeVarToQuery[P](
    		implicit m: Manifest[P], 
    		tval: Option[P] => EntityValue[P], 
    		override val context: ActivateContext) 
		extends 
			Var[P](null.asInstanceOf[P]) {
    	
    	def entityValueMock = 
    			(EntityValue.tvalFunction(fakeValueClass))(None).asInstanceOf[EntityValue[P]]
    	
        var fakeValueClass: Class[_] = _
        var originVar: FakeVarToQuery[_] = _
        override def get: Option[P] = {
        	val value =
                if (classOf[Entity].isAssignableFrom(fakeValueClass))
                    mockEntity(fakeValueClass.asInstanceOf[Class[Entity]], this)
                else
                    newInstance(fakeValueClass).asInstanceOf[P]
            Option(value.asInstanceOf[P])
        }
        override def put(value: Option[P]): Unit = {
    		throw new IllegalStateException("You can't alter vars in a predicate!")
        }
    }

    def mockEntity[E <: Entity](clazz: Class[E]): E =
    	entityMockCache.getOrElseUpdate(clazz, mockEntity[E](clazz, null)).asInstanceOf[E]
    
    def mockEntity[E <: Entity](clazz: Class[E], originVar: FakeVarToQuery[_]): E = {
        val entity = newInstance(clazz).asInstanceOf[E]
        val (idField, fields) = EntityHelper.getEntityFields(clazz.asInstanceOf[Class[Entity]])
        for ((field, typ) <- fields) {
            val ref = mockVar
            ref.fakeValueClass = typ
            ref.originVar = originVar
            field.set(entity, ref)
        }
        idField.set(entity, generateUUID)
        entity.boundVarsToEntity
        entity
    }

    def mockVar = 
    	newInstance(classOf[FakeVarToQuery[_]]).asInstanceOf[FakeVarToQuery[_]]

}