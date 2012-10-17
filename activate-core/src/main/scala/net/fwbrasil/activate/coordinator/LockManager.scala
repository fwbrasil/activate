package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable
import com.sun.org.apache.xalan.internal.xsltc.compiler.ForEach
import net.fwbrasil.activate.util.Logging

trait LockManager {
	this: CoordinatorService =>

	private type ContextId = String
	private type EntityId = String

	private class Lock {
		var readLocks = ListBuffer[ContextId]()
		var writeLock: Option[ContextId] = None
	}

	private val locks = new MutableHashMap[EntityId, Lock]() with Lockable

	def tryToAcquireLocks(
		contextId: ContextId,
		reads: Set[EntityId],
		writes: Set[EntityId]): (Set[EntityId], Set[EntityId]) = {
		val (readLocksOk, readLocksNOk) = (reads -- writes).partition(tryToAcquireReadLock(contextId, _))
		val (writeLocksOk, writeLocksNOk) = writes.partition(tryToAcquireWriteLock(contextId, _))
		if (readLocksNOk.nonEmpty || writeLocksNOk.nonEmpty) {
			readLocksOk.foreach(releaseReadLock(contextId, _))
			writeLocksOk.foreach(releaseWriteLock(contextId, _))
			(readLocksNOk, writeLocksNOk)
		} else {
			(Set(), Set())
		}
	}

	def releaseLocks(
		contextId: ContextId,
		reads: Set[EntityId],
		writes: Set[EntityId]) = {

		val readUnlocksNOk = (reads -- writes).filterNot(releaseReadLock(contextId, _))
		val writeUnlocksNOk = writes.filterNot(releaseWriteLock(contextId, _))
		(readUnlocksNOk, writeUnlocksNOk)
	}

	// PRIVATE

	private def tryToAcquireReadLock(
		contextId: ContextId,
		entityId: EntityId) = {
		!hasPendingNotification(contextId, entityId) && {
			val lock = lockFor(entityId)
			lock.synchronized {
				if (lock.writeLock.isEmpty) {
					lock.readLocks += contextId
					true
				} else
					false
			}
		}
	}

	private def tryToAcquireWriteLock(
		contextId: ContextId,
		entityId: EntityId) = {
		!hasPendingNotification(contextId, entityId) && {
			val lock = lockFor(entityId)
			lock.synchronized {
				if (lock.readLocks.isEmpty && lock.writeLock.isEmpty) {
					lock.writeLock = Option(contextId)
					true
				} else
					false
			}
		}
	}

	private def releaseReadLock(
		contextId: ContextId,
		entityId: EntityId) = {
		val lock = lockFor(entityId)
		lock.synchronized {
			val res =
				if (!lock.readLocks.contains(contextId))
					false
				else {
					lock.readLocks -= contextId
					true
				}
			cleanLockIfPossible(entityId, lock)
			res
		}
	}

	private def releaseWriteLock(
		contextId: ContextId,
		entityId: EntityId) = {

		val lock = lockFor(entityId)
		lock.synchronized {
			val res =
				if (lock.writeLock != Option(contextId))
					false
				else {
					lock.writeLock = None
					true
				}
			cleanLockIfPossible(entityId, lock)
			addNotification(contextId, entityId)
			res
		}
	}

	private def cleanLockIfPossible(entityId: EntityId, lock: Lock) = {
		//		if (lock.readLocks.isEmpty && lock.writeLock.isEmpty)
		//			locks.doWithWriteLock {
		//				locks -= entityId
		//			}
	}

	private def lockFor(entityId: EntityId) =
		locks.doWithReadLock(locks.get(entityId)).getOrElse {
			locks.doWithWriteLock {
				locks.getOrElseUpdate(entityId, new Lock)
			}
		}

}