package net.fwbrasil.activate.coordinator

import net.fwbrasil.radon.RadonContext
import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.SynchronizedSet
import net.fwbrasil.radon.util.Lockable
import scala.actors._
import scala.actors.Actor._
import scala.actors.remote._
import scala.actors.remote.RemoteActor._
import net.fwbrasil.activate.DurableContext

object Coordinator {

	RemoteActor.classLoader = getClass().getClassLoader()

	val actorName = 'coordinatorServer

	val port = Integer.parseInt(Option(System.getProperty("activate.coordinator.port")).getOrElse("5674"))

	val isServerVM =
		System.getProperty("activate.coordinator.server") == "true"

	lazy val serverOption =
		if (isServerVM)
			Option(new CoordinatorServer)
		else
			None

	def clientOption(context: DurableContext) = {
		val hostOption =
			Option(System.getProperty("activate.coordinator.serverHost"))
		hostOption.map(host => select(Node(host, port), actorName)).orElse(serverOption).map(new CoordinatorClient(context, _))
	}

}

object coordinatorServerMain extends App {
	System.setProperty("activate.coordinator.server", "true")
	require(Coordinator.serverOption.isDefined)
}