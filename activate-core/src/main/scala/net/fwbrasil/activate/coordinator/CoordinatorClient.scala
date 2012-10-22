package net.fwbrasil.activate.coordinator

import scala.actors.AbstractActor
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.DurableContext
import java.lang.Thread.UncaughtExceptionHandler
import net.fwbrasil.activate.util.Logging

class CoordinatorClient(val context: DurableContext, val server: AbstractActor) extends Logging {

	val contextId = context.contextId

	info("Coordinator client started.")

	start

	var running = true

	def start = {
		if (running)
			throw new IllegalStateException("Coordinator client already started.")
		running = true
		registerContext
	}

	def terminate = synchronized {
		if (running) {
			running = false
			deregisterContext
		}
	}

	Runtime.getRuntime.addShutdownHook(new Thread {
		override def run =
			terminate
	})

	var syncThread = CoordinatorClientSyncThread(this)

	def reinitialize = {
		syncThread.stopFlag = true
		syncThread.join
		deregisterContext
		registerContext
		syncThread = CoordinatorClientSyncThread(this)
	}

	private def registerContext =
		sendAndExpectSuccess(RegisterContext(contextId))

	private def deregisterContext =
		sendAndExpectSuccess(DeregisterContext(contextId))

	def tryToAcquireLocks(reads: Set[String], writes: Set[String]) = {
		if (reads.isEmpty && writes.isEmpty)
			(Set(), Set())
		else
			sendAndExpect(TryToAcquireLocks(contextId, reads, writes), _ match {
				case Success(request) =>
					(Set[String](), Set[String]())
				case LockFail(request, readLocksNok, writeLocksNok) =>
					(readLocksNok, writeLocksNok)
			})
	}

	def releaseLocks(reads: Set[String], writes: Set[String]) =
		sendAndExpect(ReleaseLocks(contextId, reads, writes), _ match {
			case Success(request) =>
				(Set[String](), Set[String]())
			case UnlockFail(request, readUnlocksNok, writeUnlocksNok) =>
				(readUnlocksNok, writeUnlocksNok)
		})

	def getPendingNotifications =
		sendAndExpect(GetPendingNotifications(contextId), _ match {
			case PendingNotifications(request, entitiesIds) =>
				entitiesIds
		})

	def removeNotifications(entityIds: Set[String]) =
		sendAndExpectSuccess(RemoveNotifications(contextId, entityIds))

	private def failResponse =
		throw new IllegalStateException("Invalid response")

	private def sendAndExpectSuccess(msg: CoordinatorServerRequestMessage): Unit =
		sendAndExpect(msg, _ match {
			case Success(request) => // ok!
		})

	private def sendAndExpect[R](msg: CoordinatorServerRequestMessage, handler: (CoordinatorServerReponseMessage) => R) = {
		server !? msg match {
			case Failure(request, exception) =>
				throw exception
			case other: CoordinatorServerReponseMessage =>
				if (other.request != msg)
					failResponse
				try handler(other)
				catch {
					case e: MatchError =>
						failResponse
				}
		}
	}
}