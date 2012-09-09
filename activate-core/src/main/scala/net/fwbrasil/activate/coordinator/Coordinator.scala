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

object Coordinator {

	val actorName = 'coordinatorServer

	val isServerVM =
		System.getProperty("activate.coordinator.server") == "true"

	val serverOption =
		if (isServerVM)
			Option(new CoordinatorServer)
		else
			None

	def clientOption = {
		val hostOption =
			if (isServerVM)
				Option("localhost")
			else
				Option(System.getProperty("activate.coordinator.host"))
		serverOption.orElse(hostOption.map(host => select(Node(host, 5674), actorName))).map(new CoordinatorClient(_))
	}

}