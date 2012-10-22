package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.activate.util.Logging

class ContextIsAlreadyRegistered(contextId: String) extends Exception
class ContextIsntRegistered(contextId: String) extends Exception

class NotificationList {

	private val toNotify =
		ListBuffer[String]()

	private val toRemove =
		ListBuffer[String]()

	def pendingNotification(id: String) = {
		val isPending = toNotify.contains(id)
		if (isPending) {
			toNotify -= id
			toRemove += id
		}
		isPending
	}

	def pendingNotifications(size: Int) = {
		val res = toNotify.take(size)
		toNotify.remove(0, size)
		toRemove ++= res
		res
	}

	def proccessedNotifications(ids: Iterable[String]) = {
		ids.foreach(id => {
			val index = toRemove.indexOf(id)
			if (index >= 0)
				toRemove.remove(index)
		})
	}

	def addNotification(id: String) =
		toNotify += id

	override def toString = (toNotify, toRemove).toString
}

trait NotificationManager {
	this: CoordinatorService =>

	private val notificationBlockSize =
		Integer.parseInt(
			Option(System.getProperty("activate.coordinator.notificationBlockSize"))
				.getOrElse("1000"))

	private val notifications =
		new MutableHashMap[String, NotificationList]() with Lockable

	def registerContext(contextId: String) =
		logInfo("register context " + contextId) {
			notifications.doWithWriteLock {
				if (notifications.contains(contextId))
					throw new ContextIsAlreadyRegistered(contextId)
				notifications += contextId -> new NotificationList
			}
		}

	def deregisterContext(contextId: String) =
		logInfo("deregister context " + contextId) {
			notifications.doWithWriteLock {
				if (!notifications.contains(contextId))
					throw new ContextIsntRegistered(contextId)
				notifications.remove(contextId)
			}
		}

	def getPendingNotifications(contextId: String) = {
		val list = notificationList(contextId)
		list.synchronized {
			list.pendingNotifications(notificationBlockSize).toSet
		}
	}

	def removeNotifications(contextId: String, entityIds: Set[String]) = {
		val list = notificationList(contextId)
		list.synchronized {
			list.proccessedNotifications(entityIds)
		}
	}

	private def notificationList(contextId: String) =
		notifications.doWithReadLock {
			notifications.get(contextId).getOrElse(throw new ContextIsntRegistered(contextId))
		}

	def hasPendingNotification(contextId: String, entityId: String) = {
		val list = notificationList(contextId)
		list.synchronized {
			list.pendingNotification(entityId)
		}
	}

	def addNotification(originatorContextId: String, id: String) =
		notifications.doWithReadLock {
			notifications.keys.filter(_ != originatorContextId).foreach {
				contextToNotify =>
					val list = notificationList(contextToNotify)
					list.synchronized {
						list.addNotification(id)
					}
			}
		}

}