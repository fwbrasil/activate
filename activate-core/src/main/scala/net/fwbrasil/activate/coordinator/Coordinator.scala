package net.fwbrasil.activate.coordinator

import net.fwbrasil.radon.RadonContext
import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.SynchronizedSet
import net.fwbrasil.radon.util.Lockable

//object coordinatorSTMContext extends RadonContext {
//	override val retryLimit = 1
//}
//
//import coordinatorSTMContext._

case class InvalidationNotification(entityId: String)

object coordinator {

	val pendingNotificationsMap = new MutableHashMap[String, MutableHashSet[String] with Lockable]() with Lockable

	def registerContext(contextId: String) =
		pendingNotificationsMap.doWithWriteLock {
			if (pendingNotificationsMap.contains(contextId))
				throw new IllegalStateException("Context already registered on coordinator.")
			pendingNotificationsMap.put(contextId, new MutableHashSet[String] with Lockable)
		}

	def isValidToMe(contextId: String, entityId: String) = {

	}

	def notifyWrite(contextId: String, entityId: String) = {

	}
}