package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable

class ContextIsAlreadyRegistered(contextId: String) extends Exception
class ContextIsntRegistered(contextId: String) extends Exception

class NotificationList extends Lockable {
	private val list =
		ListBuffer[String]()

	def take(i: Int) =
		list.take(i)

	def --=(ids: Iterable[String]) =
		ids.foreach(id => {
			val index = list.indexOf(id)
			if (index >= 0)
				list.remove(index)
			else
				throw new IllegalStateException("Can't find the notification.")
		})

	def contains(id: String) =
		list.contains(id)

	def +=(id: String) =
		list += id
}

trait NotificationManager {
	this: CoordinatorServer =>

	private val notificationBlockSize =
		Integer.parseInt(
			Option(System.getProperty("activate.coordinator.notificationBlockSize"))
				.getOrElse("1000"))

	// Map[ContextId, Set[EntityId]]
	private val notifications = new MutableHashMap[String, NotificationList]() with Lockable

	protected def registerContext(contextId: String) =
		notifications.doWithWriteLock {
			if (notifications.contains(contextId))
				throw new ContextIsAlreadyRegistered(contextId)
			notifications.getOrElseUpdate(contextId, new NotificationList)
		}

	protected def deregisterContext(contextId: String) =
		notifications.doWithWriteLock {
			if (!notifications.contains(contextId))
				throw new ContextIsntRegistered(contextId)
			notifications.remove(contextId)
		}

	protected def getPendingNotifications(contextId: String) = {
		val list = notificationList(contextId)
		list.doWithReadLock {
			list.take(notificationBlockSize).toSet
		}
	}

	protected def removeNotifications(contextId: String, entityIds: Set[String]) = {
		val list = notificationList(contextId)
		list.doWithWriteLock {
			list --= entityIds
		}
	}

	private def notificationList(contextId: String) =
		notifications.doWithReadLock {
			notifications.get(contextId).getOrElse(throw new ContextIsntRegistered(contextId))
		}

	protected def hasPendingNotification(contextId: String, entityId: String) = {
		val list = notificationList(contextId)
		list.doWithReadLock {
			list.contains(entityId)
		}
	}

	protected def addNotification(originatorContextId: String, id: String) =
		notifications.doWithReadLock {
			notifications.keys.filter(_ != originatorContextId).foreach {
				contextToNotify =>
					val list = notificationList(contextToNotify)
					list.doWithWriteLock {
						list += id
					}
			}
		}

}