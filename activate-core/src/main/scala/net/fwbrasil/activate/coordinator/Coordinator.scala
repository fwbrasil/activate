package net.fwbrasil.activate.coordinator

import net.fwbrasil.radon.RadonContext
import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.SynchronizedSet
import net.fwbrasil.radon.util.Lockable

object coordinatorObject {

	val notificationBlockSize = 1000

	val locks = new MutableHashMap[String, String]() with Lockable

	def tryWriteLock(contextId: String, entityIds: Set[String]): Set[String] = {
		val (locked, unlocked) = entityIds.partition(tryWriteLock(contextId, _))
		if (unlocked.isEmpty)
			addNotifications(contextId, entityIds)
		else
			writeUnlock(contextId, locked)
		unlocked
	}

	private def tryWriteLock(contextId: String, entityId: String): Boolean = {
		val isLocked = locks.doWithReadLock(locks.contains(entityId))
		if (isLocked)
			false
		else
			locks.doWithWriteLock {
				if (!locks.contains(entityId)) {
					locks += (entityId -> contextId)
					true
				} else
					false
			}
	}

	def writeUnlock(contextId: String, entityIds: Set[String]): Unit =
		entityIds.foreach(writeUnlock(contextId, _))

	private def writeUnlock(contextId: String, entityId: String): Unit =
		locks.doWithWriteLock {
			val lockedTo = locks.get(entityId)
			if (lockedTo.isEmpty || lockedTo.get != contextId)
				throw new IllegalStateException("Context doesn't have the write lock!")
			locks.remove(entityId)
		}

	val notifications = new MutableHashMap[String, MutableHashSet[String] with Lockable]() with Lockable

	def registerContext(contextId: String) =
		notifications.doWithWriteLock {
			if (notifications.contains(contextId))
				throw new IllegalStateException("Context is already registered!")
			notifications.getOrElseUpdate(contextId, new MutableHashSet[String] with Lockable)
		}

	def deregisterContext(contextId: String) =
		notifications.doWithWriteLock {
			if (!notifications.contains(contextId))
				throw new IllegalStateException("Context isn't registered!")
			notifications.remove(contextId)
		}

	private def noficationSet(contextId: String) =
		notifications.doWithReadLock {
			notifications.get(contextId).getOrElse(throw new IllegalStateException("Context is already registered!"))
		}

	def pendingNotifications(contextId: String) =
		noficationSet(contextId).take(notificationBlockSize).toSet

	def removeNotifications(contextId: String, ids: Set[String]) = {
		val set = noficationSet(contextId)
		set.doWithWriteLock {
			set --= ids
		}
	}

	def addNotifications(contextId: String, ids: Set[String]) = {
		val set = noficationSet(contextId)
		set.doWithWriteLock {
			set ++= ids
		}
	}

}