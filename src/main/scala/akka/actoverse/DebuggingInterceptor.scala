package akka.actoverse

import akka.actor.Actor.Receive
import akka.actor._

import scala.collection._
import scala.reflect.runtime.universe._

trait DebuggingInterceptor {
  def beforeStart(): Unit
  def wrapReceive(msg: Any, receive: Actor.Receive): Unit
  def wrapEnvelope(target: ActorRef, message: Any, sender: ActorRef): Any
}

object DebuggingInterceptor {
  object Null extends DebuggingInterceptor {
    override def beforeStart(): Unit = ()
    override def wrapReceive(msg: Any, receive: Receive): Unit = receive(msg)
    override def wrapEnvelope(target: ActorRef, message: Any, sender: ActorRef): Any = message
  }
}

class ConcreteDebuggingInterceptor(val actor: Actor) extends DebuggingInterceptor {
  import ResponseProtocol._

  private def dispatcher = actor.context.actorSelection(s"/user/${actor.context.system.settings.config.getString("actoverse.debugger-actor-name")}")
  private def sender: ActorRef = actor.sender()
  private def self: ActorRef = actor.self

  private var time: Long = 0
  private var uidNr: Long = 0

  private var receivedLog: mutable.Map[Long, Set[Envelope]] = new mutable.HashMap[Long, Set[Envelope]]
  private var censorships: mutable.Map[String, Censorship] = new mutable.HashMap[String, Censorship]
  private var messagePool: mutable.Map[String, Envelope] = new mutable.HashMap[String, Envelope]

  private var stateSnapshots = mutable.Map[Long, immutable.Map[TermSymbol, Any]]()

  def beforeStart(): Unit = {
    dispatcher ! ActorCreated(
      ActorInfo(
        actor.getClass.getSimpleName,
        takeStateSnapshot(0)
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
        self.tell(ResentMessage(message), message.senderRef)
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
    case ResentMessage(e) => Some(e)
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

  def wrapReceive(msg: Any, receive: Actor.Receive) : Unit = {
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
            receivedLog += (time -> new mutable.HashSet[Envelope])
          }
          receivedLog(time) += e
          // calls original receive
          receive(message)
          // take a snapshot
          val currentState = takeStateSnapshot(time)
          dispatcher ! ActorUpdated(currentState, time, self.path)
        case _ => throw new Exception("Envelope-unwrapped message arrived")
      }
    }
  }

  def wrapEnvelope(target: ActorRef, message: Any, sender: ActorRef): Any = {
    // send to ws api
    uidNr += 1
    val uid = s"${self.path.name}-$uidNr"
    dispatcher.tell(
      SendMessage(
        MessageBody(sender.path, target.path, message, time, uid),
        time, sender.path
      )
    , Actor.noSender)

    Envelope(message, time, uid, sender)
  }

  def takeStateSnapshot(serialNr: Long): immutable.Map[String, Any] = {
    val im = runtimeMirror(actor.getClass.getClassLoader).reflect(actor)
    val targetFields = im.symbol.selfType.members
      .collect { case s: TermSymbol if s.isVar => s }
    stateSnapshots(serialNr) = targetFields.map { field =>
      (field, im.reflectField(field).get)
    }.toMap

    // convert to Map[String, Any]
    stateSnapshots(serialNr).map({ case (field, value) =>
      (field.name.toString, value)
    })
  }

  def recoverStateUntil(timestamp: Long): immutable.Map[String, Any] = {
    val im = runtimeMirror(actor.getClass.getClassLoader).reflect(actor)
    // filterKeys does not return mutable one
    stateSnapshots = stateSnapshots.filter(_._1 < timestamp)
    val latestState = stateSnapshots(stateSnapshots.keys.max)
    latestState.foreach { case (field, value) =>
      im.reflectField(field).set(value) }

    // convert to Map[String, Any]
    latestState.map({ case (field, value) =>
      (field.name.toString, value)
    })
  }
}