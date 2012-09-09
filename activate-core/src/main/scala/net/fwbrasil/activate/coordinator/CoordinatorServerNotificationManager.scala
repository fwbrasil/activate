package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable

class ContextIsAlreadyRegistered(contextId: String) extends Exception
class ContextIsntRegistered(contextId: String) extends Exception

trait NotificationManager {
	this: CoordinatorServer =>

	private val notificationBlockSize = 1000

	// Map[ContextId, Set[EntityId]]
	private val notifications = new MutableHashMap[String, MutableHashSet[String] with Lockable]() with Lockable

	protected def registerContext(contextId: String) =
		notifications.doWithWriteLock {
			if (notifications.contains(contextId))
				throw new ContextIsAlreadyRegistered(contextId)
			notifications.getOrElseUpdate(contextId, new MutableHashSet[String] with Lockable)
		}

	protected def deregisterContext(contextId: String) =
		notifications.doWithWriteLock {
			if (!notifications.contains(contextId))
				throw new ContextIsntRegistered(contextId)
			notifications.remove(contextId)
		}

	protected def getPendingNotifications(contextId: String) = {
		val set = noficationSet(contextId)
		set.doWithReadLock {
			set.take(notificationBlockSize).toSet
		}
	}

	protected def removeNotifications(contextId: String, entityIds: Set[String]) = {
		val set = noficationSet(contextId)
		set.doWithWriteLock {
			set --= entityIds
		}
	}

	private def noficationSet(contextId: String) =
		notifications.doWithReadLock {
			notifications.get(contextId).getOrElse(throw new ContextIsntRegistered(contextId))
		}

	protected def hasPendingNotification(contextId: String, entityId: String) = {
		val set = noficationSet(contextId)
		set.doWithReadLock {
			set.contains(entityId)
		}
	}

	protected def addNotifications(contextId: String, ids: Set[String]) =
		notifications.doWithReadLock {
			notifications.keys.filter(_ != contextId).foreach {
				contextToNotify =>
					val set = noficationSet(contextToNotify)
					set.doWithWriteLock {
						set ++= ids
					}
			}
		}

}