package net.fwbrasil.activate.coordinator

import scala.actors.AbstractActor
import net.fwbrasil.activate.util.ManifestUtil._

class CoordinatorClient(val server: AbstractActor) {

	def registerContext(contextId: String) =
		sendAndExpectSuccess(RegisterContext(contextId))

	def deregisterContext(contextId: String) =
		sendAndExpectSuccess(DeregisterContext(contextId))

	def tryToAcquireLocks(contextId: String, entityIds: Set[String]) =
		sendAndExpect(TryToAcquireLocks(contextId, entityIds), _ match {
			case Success(request) =>
				Set[String]()
			case LockFail(request, failedIds) =>
				failedIds
		})

	def releaseLocks(contextId: String, entityIds: Set[String]) =
		sendAndExpectSuccess(ReleaseLocks(contextId, entityIds))

	def getPendingNotifications(contextId: String) =
		sendAndExpect(GetPendingNotifications(contextId), _ match {
			case PendingNotifications(request, entitiesIds) =>
				entitiesIds
		})

	def removeNotifications(contextId: String, entityIds: Set[String]) =
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