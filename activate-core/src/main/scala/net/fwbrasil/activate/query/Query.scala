package net.fwbrasil.activate.query

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.ActivateContext
import From.runAndClearFrom

trait QueryContext extends QueryValueContext with OperatorContext {

	private[activate] def queryInternal[E1 <: Entity: Manifest](f: (E1) => Query[Product]) =
		runAndClearFrom {
			f(mockEntity[E1])
		}

	def query[S <: Product, E1 <: Entity: Manifest](f: (E1) => Query[S]) =
		runAndClearFrom {
			f(mockEntity[E1])
		}

	def query[S <: Product, E1 <: Entity: Manifest, E2 <: Entity: Manifest](f: (E1, E2) => Query[S]) =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2])
		}

	def query[S <: Product, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest](f: (E1, E2, E3) => Query[S]) =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3])
		}

	def query[S <: Product, E1 <: Entity: Manifest, E2 <: Entity: Manifest, E3 <: Entity: Manifest, E4 <: Entity: Manifest](f: (E1, E2, E3, E4) => Query[S]) =
		runAndClearFrom {
			f(mockEntity[E1],
				mockEntity[E2],
				mockEntity[E3],
				mockEntity[E4])
		}

	def mockEntity[E <: Entity: Manifest]: E = {
		val mockEntity = QueryMocks.mockEntity(manifest[E].erasure.asInstanceOf[Class[E]])
		From.createAndRegisterEntitySource(manifest[E].erasure.asInstanceOf[Class[E]], mockEntity);
		mockEntity
	}

	def where(value: Criteria) =
		Where(value)

	def executeQuery[S](query: Query[S]): List[S]

}

case class Query[S](from: From, where: Where, select: Select) {
	def execute: List[S] = {
		val context =
			(for (src <- from.entitySources)
				yield ActivateContext.contextFor(src.entityClass)).onlyOne
		context.executeQuery(this)
	}


	def orderBy[V1, T1 <% QuerySelectValue[V1]]
	          (tuple: T1) =
      	orderedQuery(Tuple1(tuple))
		
	def orderBy[V1, T1 <% QuerySelectValue[V1], 
	           V2, T2 <% QuerySelectValue[V2]]
	          (tuple: Tuple2[T1, T2]) = 
		orderedQuery(tuple)
	
	def orderBy[V1, T1 <% QuerySelectValue[V1], 
	           V2, T2 <% QuerySelectValue[V2],
	           V3, T3 <% QuerySelectValue[V3]]
	          (tuple: Tuple3[T1, T2, T3]) = 
		orderedQuery(tuple)
	
	def orderBy[V1, T1 <% QuerySelectValue[V1], 
	           V2, T2 <% QuerySelectValue[V2],
	           V3, T3 <% QuerySelectValue[V3],
	           V4, T4 <% QuerySelectValue[V4]]
	          (tuple: Tuple4[T1, T2, T3, T4]) = 
		orderedQuery(tuple)
		
	private[this] def orderedQuery(tuple: Product) = {
		val values = tuple.productElements.toList.asInstanceOf[List[QuerySelectValue[_]]]
		OrderedQuery[S](from, where, select, OrderBy(values:_*))
	}
	
	override def toString = from + " => where" + where + " select " + select + ""
}

case class OrderedQuery[S](override val from: From, override val where: Where, override val select: Select, orderBy: OrderBy)
	extends Query[S](from, where, select) {
	override def toString = super.toString + orderBy.toString
}

case class Select(values: QuerySelectValue[_]*) {
	override def toString = "(" + values.mkString(", ") + ")"
}

case class OrderBy(values: QuerySelectValue[_]*) {
	override def toString = " orderBy (" + values.mkString(", ") + ")"
}

