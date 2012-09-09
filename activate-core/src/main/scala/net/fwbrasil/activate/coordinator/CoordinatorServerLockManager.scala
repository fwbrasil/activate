package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable

class ContextDoesntOwnTheLock(contextId: String, entityId: String, currentOwnerContextIdOption: Option[String]) extends Exception

trait LockManager {
	this: CoordinatorServer =>

	// Map[EntityId, (ContextId, NumOfLocks, Timestamp)]
	private val locks = new MutableHashMap[String, (String, Int, Long)]() with Lockable

	protected def tryToAcquireLocks(contextId: String, entityIds: Set[String]): Set[String] = {
		val (locked, unlocked) = entityIds.partition(tryLock(contextId, _))
		if (unlocked.isEmpty)
			addNotifications(contextId, entityIds)
		else
			releaseLocks(contextId, locked)
		unlocked
	}

	protected def releaseLocks(contextId: String, entityIds: Set[String]): Unit =
		entityIds.foreach(releaseLock(contextId, _))

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

	private def releaseLock(contextId: String, entityId: String): Unit =
		locks.doWithWriteLock {
			val lockedToOption = locks.get(entityId)
			if (lockedToOption.isEmpty || lockedToOption.get._1 != contextId)
				throw new ContextDoesntOwnTheLock(contextId, entityId, lockedToOption.map(_._1))

			lockedToOption.map { tuple =>
				val (contextId, numOfLocks, timestamp) = tuple
				if (numOfLocks == 1)
					locks.remove(entityId)
				else
					locks(entityId) = (contextId, numOfLocks - 1, System.currentTimeMillis)
			}
		}
}
