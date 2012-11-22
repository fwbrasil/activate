package net.fwbrasil.activate.storage.graph

import com.tinkerpop.blueprints.{ Edge => BPEdge }
import com.tinkerpop.blueprints.Graph
import com.tinkerpop.blueprints.IndexableGraph
import com.tinkerpop.blueprints.{ Vertex => BPVertex }
import com.tinkerpop.blueprints.util.wrappers.id.IdGraph
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import com.tinkerpop.blueprints.KeyIndexableGraph

trait Vertex extends Entity {
	def ->[E <: Edge[_, _]](f: ((E) => Unit)*) = List()
}
trait Edge[A <: Vertex, B <: Vertex] extends Entity {
	val from: A
	val to: B
}

class GraphContext(val graph: Graph) extends ActivateContext {
	val storage = GraphStorage(graph)
	type Vertex = net.fwbrasil.activate.storage.graph.Vertex
	type Edge[A <: Vertex, B <: Vertex] = net.fwbrasil.activate.storage.graph.Edge[A, B]

	override protected lazy val runMigrationAtStartup = false
}

case class GraphStorage(pGraph: Graph) extends MarshalStorage[Graph] {

	lazy val graph: Graph =
		if (!pGraph.getFeatures.ignoresSuppliedIds)
			pGraph
		else
			pGraph match {
				case graph: KeyIndexableGraph =>
					new IdGraph(graph)
				case other =>
					throw new UnsupportedOperationException("Graph does not have support for custom IDs and it is not indexable.")
			}

	def directAccess =
		graph

	override def store(
		statements: List[MassModificationStatement],
		insertList: List[(Entity, Map[String, StorageValue])],
		updateList: List[(Entity, Map[String, StorageValue])],
		deleteList: List[(Entity, Map[String, StorageValue])]): Unit = {

		store(vertexFirst(insertList),
			vertex => {
				val v = graph.addVertex(vertex.id)
				println(v.getId)
				v
			},
			(edge, properties) => {
				val fromVertex = graph.getVertex(nativeValue(properties("from")))
				val toVertex = graph.getVertex(nativeValue(properties("to")))
				val label = EntityHelper.getEntityName(edge.getClass)
				graph.addEdge(edge.id, fromVertex, toVertex, label)
			})

		store(updateList,
			vertex => graph.getVertex(vertex.id),
			(edge, properties) => graph.getEdge(edge.id)
		)

		for ((entity, properties) <- edgeFirst(deleteList))
			entity match {
				case vertex: Vertex =>
					val graphVertex = graph.getVertex(vertex.id)
					graph.removeVertex(graphVertex)
				case edge: Edge[Vertex, Vertex] =>
					val graphEdge = graph.getEdge(edge.id)
					graph.removeEdge(graphEdge)
			}

	}

	private def vertexFirst(list: List[(Entity, Map[String, StorageValue])]) =
		list.sortBy(each => if (classOf[Vertex].isAssignableFrom(each._1.getClass)) 0 else 1)

	private def edgeFirst(list: List[(Entity, Map[String, StorageValue])]) =
		vertexFirst(list).reverse

	private def store(
		list: List[(Entity, Map[String, StorageValue])],
		vertexProducer: (Vertex) => BPVertex,
		edgeProducer: (Edge[Vertex, Vertex], Map[String, StorageValue]) => BPEdge) =
		for ((entity, properties) <- list) {
			entity match {
				case vertex: Vertex =>
					val graphVertex = vertexProducer(vertex)
					for ((property, value) <- properties; if property != "id")
						graphVertex.setProperty(property, nativeValue(value))
				case edge: Edge[Vertex, Vertex] =>
					val graphEdge = edgeProducer(edge, properties)
					for ((property, value) <- properties; if property != "id")
						graphEdge.setProperty(property, nativeValue(value))
			}
		}

	private def nativeValue(storageValue: StorageValue) =
		storageValue match {
			case value: IntStorageValue =>
				value.value.map(_.intValue).getOrElse(null)
			case value: LongStorageValue =>
				value.value.map(_.longValue).getOrElse(null)
			case value: BooleanStorageValue =>
				value.value.map(_.booleanValue).getOrElse(null)
			case value: StringStorageValue =>
				value.value.getOrElse(null)
			case value: FloatStorageValue =>
				value.value.map(_.doubleValue).getOrElse(null)
			case value: DateStorageValue =>
				value.value.getOrElse(null)
			case value: DoubleStorageValue =>
				value.value.map(_.doubleValue).getOrElse(null)
			case value: BigDecimalStorageValue =>
				value.value.map(_.doubleValue).getOrElse(null)
			case value: ListStorageValue =>
				throw new UnsupportedOperationException("list on graph")
			case value: ByteArrayStorageValue =>
				value.value.getOrElse(null)
			case value: ReferenceStorageValue =>
				value.value.getOrElse(null)
		}

	override def migrateStorage(action: ModifyStorageAction): Unit = {

	}

	def query(queryInstance: Query[_], expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
		List()
	}

}