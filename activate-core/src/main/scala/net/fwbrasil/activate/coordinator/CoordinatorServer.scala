package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable
import scala.actors._
import scala.actors.Actor._
import scala.actors.remote._
import scala.actors.remote.RemoteActor._

sealed trait CoordinatorServerMessage

trait CoordinatorServerRequestMessage extends CoordinatorServerMessage
case class RegisterContext(contextId: String) extends CoordinatorServerRequestMessage
case class DeregisterContext(contextId: String) extends CoordinatorServerRequestMessage
case class TryToAcquireLocks(contextId: String, entityIds: Set[String]) extends CoordinatorServerRequestMessage
case class ReleaseLocks(contextId: String, entityIds: Set[String]) extends CoordinatorServerRequestMessage
case class GetPendingNotifications(contextId: String) extends CoordinatorServerRequestMessage
case class RemoveNotifications(contextId: String, entityIds: Set[String]) extends CoordinatorServerRequestMessage

trait CoordinatorServerReponseMessage extends CoordinatorServerMessage {
	val request: CoordinatorServerRequestMessage
}
case class Success(request: CoordinatorServerRequestMessage) extends CoordinatorServerReponseMessage
case class Failure(request: CoordinatorServerRequestMessage, e: Throwable) extends CoordinatorServerReponseMessage

case class PendingNotifications(request: CoordinatorServerRequestMessage, entitiesIds: Set[String]) extends CoordinatorServerReponseMessage

case class LockFail(request: CoordinatorServerRequestMessage, failedIds: Set[String]) extends CoordinatorServerReponseMessage

class CoordinatorServer extends Actor with LockManager with NotificationManager {
	start
	def act {
		alive(5674)
		register(Coordinator.actorName, self)
		loop {
			receive {
				case msg: RegisterContext =>
					runAndReplyIfSuccess(msg)(registerContext(msg.contextId))
				case msg: DeregisterContext =>
					runAndReplyIfSuccess(msg)(deregisterContext(msg.contextId))
				case msg: TryToAcquireLocks =>
					runAndReply(msg)(tryToAcquireLocks(msg.contextId, msg.entityIds)) {
						failed =>
							if (failed.isEmpty)
								Success(msg)
							else
								LockFail(msg, failed)
					}

				case msg: ReleaseLocks =>
					runAndReplyIfSuccess(msg)(releaseLocks(msg.contextId, msg.entityIds))
				case msg: GetPendingNotifications =>
					runAndReply(msg)(getPendingNotifications(msg.contextId))(PendingNotifications(msg, _))
				case msg: RemoveNotifications =>
					runAndReplyIfSuccess(msg)(removeNotifications(msg.contextId, msg.entityIds))
			}
		}
	}

	private def runAndReplyIfSuccess[R](msg: CoordinatorServerRequestMessage)(fAction: => R): Unit =
		runAndReply(msg)(fAction)(_ => new Success(msg))

	private def runAndReply[R](msg: CoordinatorServerRequestMessage)(fAction: => R)(fResponse: (R) => CoordinatorServerReponseMessage): Unit =
		reply(
			try
				fResponse(fAction)
			catch {
				case e =>
					Failure(msg, e)
			})

}

