package net.fwbrasil.activate.graph

import net.fwbrasil.activate.ActivateContext
import com.tinkerpop.blueprints.impls.neo4j.Neo4jGraph
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import net.fwbrasil.activate.storage.graph.GraphContext
import com.tinkerpop.blueprints.impls.tg.TinkerGraph

object graphPersistenceContext extends GraphContext(new Neo4jGraph("/tmp/my_graph"))
import graphPersistenceContext._

class Person(var name: String) extends Vertex
class Knows(val from: Person, val to: Person, var since: Int) extends Edge[Person, Person]

@RunWith(classOf[JUnitRunner])
class GraphSpecs extends SpecificationWithJUnit {

	"Graph storage" should {
		"create new vertex" in {
			transactional {
				val flavio = new Person("flavio")
				val felipe = new Person("felipe")
				new Knows(flavio, felipe, 2012)
			}
			transactional {
				all[Person].map(_.name).toSet mustEqual Set("flavio", "felipe")
				//				val list = query {
				//					(p: Person, knows: Knows) => where((p.name :== "flavio") :&& (p.id :== knows.from)) select (p.name)
				//				}
				val flavio = select[Person].where(_.name :== "flavio").head
				val felipe = flavio.->[Knows]()
				//				list mustEqual List("flavio")
			}
			ok
		}
	}

}