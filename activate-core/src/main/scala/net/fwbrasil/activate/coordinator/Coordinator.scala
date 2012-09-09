package net.fwbrasil.activate.coordinator

import net.fwbrasil.radon.RadonContext
import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.SynchronizedSet
import net.fwbrasil.radon.util.Lockable

object coordinatorObject {

	val notificationBlockSize = 1000

	// Map[EntityId, (ContextId, NumOfLocks, Timestamp)]
	val locks = new MutableHashMap[String, (String, Int, Long)]() with Lockable

	def tryLock(contextId: String, entityIds: Set[String]): Set[String] = {
		val (locked, unlocked) = entityIds.partition(tryLock(contextId, _))
		if (unlocked.isEmpty)
			addNotifications(contextId, entityIds)
		else
			unlock(contextId, locked)
		unlocked
	}

	private def lockPromotion(lockable: Lockable)(fCondition: => Boolean)(fAction: => Unit) = {
		val write =
			lockable.doWithReadLock {
				fCondition
			}
		if (write)
			lockable.doWithWriteLock {
				if (fCondition) {
					fAction
					true
				} else false
			}
		else false
	}

	private def tryLock(contextId: String, entityId: String): Boolean = {

		lazy val pendingNotification = hasPendingNotification(contextId, entityId)
		lazy val currentLockOption = locks.doWithReadLock(locks.get(entityId))

		val promotedLock =
			lockPromotion(locks)(locks.get(entityId) == Some(contextId)) {
				locks(entityId) = {
					val old = locks(entityId)
					(contextId, old._2 + 1, System.currentTimeMillis)
				}
			}

		promotedLock ||
			(!pendingNotification &&
				locks.doWithWriteLock {
					if (!locks.contains(entityId)) {
						locks += (entityId -> (contextId, 1, System.currentTimeMillis))
						true
					} else
						false
				})
	}

	def unlock(contextId: String, entityIds: Set[String]): Unit =
		entityIds.foreach(unlock(contextId, _))

	private def unlock(contextId: String, entityId: String): Unit =
		locks.doWithWriteLock {
			val lockedToOption = locks.get(entityId)
			if (lockedToOption.isEmpty || lockedToOption.get._1 != contextId)
				throw new IllegalStateException("Context doesn't own the lock!")

			lockedToOption.map { tuple =>
				val (contextId, numOfLocks, timestamp) = tuple
				if (numOfLocks == 1)
					locks.remove(entityId)
				else
					locks(entityId) = (contextId, numOfLocks - 1, System.currentTimeMillis)
			}
		}

	// Map[ContextId, Set[EntityId]]
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

	private def hasPendingNotification(contextId: String, entityId: String) = {
		val set = noficationSet(contextId)
		set.doWithReadLock {
			set.contains(entityId)
		}
	}

	def pendingNotifications(contextId: String) = {
		val set = noficationSet(contextId)
		set.doWithReadLock {
			set.take(notificationBlockSize).toSet
		}
	}

	def removeNotifications(contextId: String, ids: Set[String]) = {
		val set = noficationSet(contextId)
		set.doWithWriteLock {
			set --= ids
		}
	}

	def addNotifications(contextId: String, ids: Set[String]) =
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