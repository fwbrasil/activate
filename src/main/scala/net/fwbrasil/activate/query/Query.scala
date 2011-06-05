package net.fwbrasil.activate.query

import net.fwbrasil.activate.entity._
import From.runAndClearFrom

trait QueryContext extends QueryValueContext with OperatorContext {
	
	private[activate] def queryInternal[E1 <: Entity: Manifest]
	          (f: (E1) => Query[Product]) =
	    runAndClearFrom {
			f(mockEntity[E1])
	    }
	
	def query[S <: Product, 
	          E1 <: Entity: Manifest]
	          (f: (E1) => Query[S]) =
	    runAndClearFrom {
			f(mockEntity[E1])
	    }
	
	def query[S <: Product,
	          E1 <: Entity: Manifest, 
	          E2 <: Entity: Manifest]
	          (f: (E1, E2) => Query[S]) =
	    runAndClearFrom {
			f(mockEntity[E1], 
			  mockEntity[E2])
		}
	
	def query[S <: Product,
	          E1 <: Entity: Manifest, 
	          E2 <: Entity: Manifest,
	          E3 <: Entity: Manifest]
	          (f: (E1, E2, E3) => Query[S]) =
	    runAndClearFrom {
			f(mockEntity[E1], 
			  mockEntity[E2],
			  mockEntity[E3])
		}
	
	def query[S <: Product,
	          E1 <: Entity: Manifest, 
	          E2 <: Entity: Manifest,
	          E3 <: Entity: Manifest,
	          E4 <: Entity: Manifest]
	          (f: (E1, E2, E3, E4) => Query[S]) = 
		runAndClearFrom {
			f(mockEntity[E1], 
			  mockEntity[E2],
			  mockEntity[E3],
			  mockEntity[E4])
		}
	
	def mockEntity[E <: Entity: Manifest]: E = {
		val mockEntity = QueryMocks.mockEntity(manifest[E].erasure.asInstanceOf[Class[E]])
		From.createAndRegisterEntitySource(mockEntity);
		mockEntity
	}
	
	def where(value: Criteria) = 
		Where(value)
	
	
	def executeQuery[S](query: Query[S]): List[S]
	
	
}

case class Query[S](from: From, where: Where, select: Select) {
	def execute(implicit context: QueryContext): List[S] = context.executeQuery(this)
	override def toString = from + " => where" + where + " select "+ select + ""
}

case class Select(values: QuerySelectValue[_]*){
	override def toString = "(" + values.mkString(", ") + ")"
}

