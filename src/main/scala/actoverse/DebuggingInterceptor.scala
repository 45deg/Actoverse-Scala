package actoverse

import akka.actor._
import actoverse._
import actoverse._
import akka.contrib.pattern.ReceivePipeline
import scala.collection.mutable._

import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.{Success, Failure}

case class Envelope(data: Any, time: Long, uid: String, senderRef: ActorRef)
case class SkipCensorship(envelope: Envelope)

trait DebuggingInterceptor extends ReceivePipeline with Actor with SnapShotTaker {
  import ResponseProtocol._

  private var time: Long = 0
  private var uidNr: Long = 0
  private def dispatcher = context.actorSelection("/user/__debugger")
  private var receivedLog: Map[Long, Set[Envelope]] = new HashMap[Long, Set[Envelope]]
  private var censorships: Map[String, Censorship] = new HashMap[String, Censorship]
  private var messagePool: Map[String, Envelope] = new HashMap[String, Envelope]

  override def preStart() {
    super.preStart()
    dispatcher ! ActorCreated(
      ActorInfo(
        getClass.getName,
        takeStateSnapshot(0)
      ),
      0,
      self.path
    )
    dispatcher ! DeliveryCommand.NewActor(self)
  }

  /* the pipeline handles Rollback messages */
  pipelineInner {
    case RequestProtocol.Rollback(time) =>
      // recovers state
      val latestState = recoverStateUntil(time)
      if (time - 1 < this.time) {
        this.time = time - 1
      }
      dispatcher ! ActorReplaced(
        latestState,
        time,
        self.path
      )
      sender ! 1
      ReceivePipeline.HandledCompletely // break the chain
    case RequestProtocol.ResendMessage(time) =>
      import scala.concurrent.ExecutionContext.Implicits.global
      val resendMessages = receivedLog.getOrElse(time, List.empty) // orelse
      receivedLog = receivedLog.filter(_._1 < time)
      resendMessages.foreach { message =>
        self.tell(message, message.senderRef)
      }
      ReceivePipeline.HandledCompletely
    case RequestProtocol.AddCensorship(id, censorshipType, value) =>
      censorships(id) = Censorship.fromJson(censorshipType, value)
      sender ! 1
      ReceivePipeline.HandledCompletely
    case RequestProtocol.RemoveCensorship(id) =>
      censorships -= id
      sender ! 1
      ReceivePipeline.HandledCompletely
    case RequestProtocol.SelectFromPool(id) =>
      if(messagePool.contains(id)) {
        val msg = messagePool(id)
        messagePool -= id
        // Inner(msg) does not work because `sender` can't be overwritten.
        self.tell(SkipCensorship(msg), msg.senderRef)
      }
      ReceivePipeline.HandledCompletely
  }

  /* the pipeline that convert from raw messages to envelopes */
  pipelineInner {
    case e: Envelope => ReceivePipeline.Inner(e)
    case e: SkipCensorship => ReceivePipeline.Inner(e)
    case data =>
      uidNr += 1
      ReceivePipeline.Inner(Envelope(data, time, s"unknown-$uidNr", sender))
  }

  /* pipeline that filter messages */
  pipelineInner {
    case e @ Envelope(message, timestamp, uid, senderRef)  =>
      val isCensored = censorships.exists { case (id, c) =>
        val result = c.check(senderRef.path, self.path, message)
        //print(s"CENSOR $id $result")
        result
      }
      if (isCensored) {
        // filtered
        messagePool(uid) = e
        dispatcher ! AddPool(
          MessageBody(senderRef.path, self.path, message, timestamp, uid),
          time, self.path
        )
        // addpool
        ReceivePipeline.HandledCompletely
      } else {
        // pass
        ReceivePipeline.Inner(e)
      }
    case SkipCensorship(e) => ReceivePipeline.Inner(e)
  }

  /* Pipeline that send "received" messages to websocket clients */
  pipelineInner { case e @ Envelope(message, timestamp, uid, senderRef) =>
    // increments my clock
    time = Math.max(timestamp, time) + 1
    // sends to ws api
    dispatcher ! ReceivedMessage(
      MessageBody(senderRef.path, self.path, message, timestamp, uid ),
      time, self.path
    )
    // add to log
    if (!receivedLog.contains(time)) {
      receivedLog += (time -> new HashSet[Envelope])
    }
    receivedLog(time) += e
    // calls original receive
    ReceivePipeline.Inner(message).andAfter {
      // after that takes a snapshot and sends to ws api
      val currentState = takeStateSnapshot(time)
      dispatcher ! ActorUpdated(currentState, time, self.path)
    }
  }

  implicit class PimpedActorRef(target: ActorRef) {
    def !+(message: Any)(implicit sender: ActorRef = Actor.noSender): Unit = {
      // send to ws api
      uidNr += 1
      val uid = s"${self.path.name}-$uidNr"
      dispatcher.tell(
        SendMessage(
          MessageBody(sender.path, target.path, message, time, uid),
          time, sender.path
        )
      , Actor.noSender)

      //
      val envelope = Envelope(message, time, uid, sender)
      target.tell(envelope, sender)
    }
  }
}
