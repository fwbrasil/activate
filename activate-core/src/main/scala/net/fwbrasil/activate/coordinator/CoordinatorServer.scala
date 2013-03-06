package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable
import scala.actors._
import scala.actors.Actor._
import scala.actors.remote._
import scala.actors.remote.RemoteActor._
import net.fwbrasil.activate.util.Logging

sealed trait CoordinatorServerMessage

trait CoordinatorServerRequestMessage extends CoordinatorServerMessage
case class RegisterContext(contextId: String) extends CoordinatorServerRequestMessage
case class DeregisterContext(contextId: String) extends CoordinatorServerRequestMessage
case class TryToAcquireLocks(contextId: String, reads: Set[String], writes: Set[String]) extends CoordinatorServerRequestMessage
case class ReleaseLocks(contextId: String, reads: Set[String], writes: Set[String]) extends CoordinatorServerRequestMessage
case class GetPendingNotifications(contextId: String) extends CoordinatorServerRequestMessage
case class RemoveNotifications(contextId: String, entityIds: Set[String]) extends CoordinatorServerRequestMessage

trait CoordinatorServerReponseMessage extends CoordinatorServerMessage {
    val request: CoordinatorServerRequestMessage
}

case class Success(request: CoordinatorServerRequestMessage) extends CoordinatorServerReponseMessage
case class Failure(request: CoordinatorServerRequestMessage, e: Throwable) extends CoordinatorServerReponseMessage

case class PendingNotifications(request: CoordinatorServerRequestMessage, entitiesIds: Set[String]) extends CoordinatorServerReponseMessage

case class LockFail(request: CoordinatorServerRequestMessage, readLocksNok: Set[String], writeLocksNok: Set[String]) extends CoordinatorServerReponseMessage
case class UnlockFail(request: CoordinatorServerRequestMessage, readUnlocksNok: Set[String], writeUnlocksNok: Set[String]) extends CoordinatorServerReponseMessage

class CoordinatorService extends LockManager
    with NotificationManager with Logging

object coordinatorServiceSingleton extends CoordinatorService

class CoordinatorServer
        extends DaemonActor
        with Logging {

    import coordinatorServiceSingleton._

    start

    info("Coordinator server started.")

    def act {
        alive(Coordinator.port)
        register(Coordinator.actorName, self)
        loop {
            receive {
                case msg: RegisterContext =>
                    runAndReplyIfSuccess(msg)(registerContext(msg.contextId))
                case msg: DeregisterContext =>
                    runAndReplyIfSuccess(msg)(deregisterContext(msg.contextId))
                case msg: TryToAcquireLocks =>
                    runAndReply(msg)(tryToAcquireLocks(msg.contextId, msg.reads, msg.writes)) {
                        failed =>
                            if (failed._1.isEmpty && failed._2.isEmpty)
                                Success(msg)
                            else
                                LockFail(msg, failed._1, failed._2)
                    }

                case msg: ReleaseLocks =>
                    runAndReply(msg)(releaseLocks(msg.contextId, msg.reads, msg.writes)) {
                        failed =>
                            if (failed._1.isEmpty && failed._2.isEmpty)
                                Success(msg)
                            else
                                UnlockFail(msg, failed._1, failed._2)
                    }
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
                case e: Throwable =>
                    Failure(msg, e)
            })

}

