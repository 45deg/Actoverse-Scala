package akka.actoverse

import akka.actor._
import scala.collection.mutable._

case class Envelope(data: Any, time: Long, uid: String, senderRef: ActorRef)
case class SkipCensorship(envelope: Envelope)

trait DebuggingInterceptor extends Actor {
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
        getClass.getSimpleName,
        this.asInstanceOf[SnapShotTaker].takeStateSnapshot(0)
      ),
      0,
      self.path
    )
    dispatcher ! DeliveryCommand.NewActor(self)
  }

  /* the pipeline handles Rollback messages */
  def processMetaMessage(msg: Any): Option[Any] = msg match {
    case RequestProtocol.Rollback(time) =>
      // recovers state
      val latestState = this.asInstanceOf[SnapShotTaker].recoverStateUntil(time)
      if (time - 1 < this.time) {
        this.time = time - 1
      }
      dispatcher ! ActorReplaced(
        latestState,
        time,
        self.path
      )
      sender ! 1
      None
    case RequestProtocol.ResendMessage(time) =>
      // messages that are received at `time`
      val receivedAtTime = receivedLog.getOrElse(time, List.empty)
      // separate before/after `time`
      val (before, after) = receivedLog.partition(_._1 < time)
      // rewrite message log entries after `time`
      receivedLog = before
      // messages that was sent before `time` though received after `time`
      val cuttingMessages = after.flatMap(_._2.filter(_.time < time))
      // messages to be resend
      val resendMessages = receivedAtTime ++ cuttingMessages
      resendMessages.foreach { message =>
        self.tell(message, message.senderRef)
      }
      None
    case RequestProtocol.AddCensorship(id, censorshipType, value) =>
      censorships(id) = Censorship.fromJson(censorshipType, value)
      sender ! 1
      None
    case RequestProtocol.RemoveCensorship(id) =>
      censorships -= id
      sender ! 1
      None
    case RequestProtocol.SelectFromPool(id) =>
      if (messagePool.contains(id)) {
        val msg = messagePool(id)
        messagePool -= id
        // Inner(msg) does not work because `sender` can't be overwritten.
        self.tell(SkipCensorship(msg), msg.senderRef)
      }
      None
    case e: Envelope => Some(e)
    case e: SkipCensorship => Some(e)
    case data =>
      uidNr += 1
      Some(Envelope(data, time, s"unknown-$uidNr", sender))
}

  /* pipeline that filter messages */
  def processCensorship(msg: Any): Option[Any] = msg match {
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
        None
      } else {
        Some(e)
      }
    case SkipCensorship(e) => Some(e)
  }

  /* Pipeline that send "received" messages to websocket clients */
  /**
    * INTERNAL API.
    */

  def wrapReceive(msg: Any, receive: Receive) : Unit = {
    for {
      msg1 <- processMetaMessage(msg)
      msg2 <- processCensorship(msg1)
    } yield {
      msg2 match {
        case e @ Envelope(message, timestamp, uid, senderRef) =>
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
          receive(message)
          val currentState = this.asInstanceOf[SnapShotTaker].takeStateSnapshot(time)
          dispatcher ! ActorUpdated(currentState, time, self.path)
        case _ => throw new Exception("Envelope-unwrapped message arrived")
      }
    }
  }

  override protected[akka] def aroundReceive(receive: Receive, msg: Any): Unit = {
    super.aroundReceive({ case m => wrapReceive(m, receive) }, msg)
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
