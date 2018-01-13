package akka.actoverse

/*
  Handles sending messsages to websocket clients
*/

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.util.Success
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.collection.mutable._
import akka.http.scaladsl.model.ws.TextMessage

// add a new client

object DeliveryCommand {
  case class NewClient(subscriber: ActorRef)
  case class NewActor(ref: ActorRef)
}

class DeliveryActor(system: ActorSystem) extends Actor {
  import akka.actoverse.ResponseProtocol._
  import context.dispatcher
  implicit val timeout = Timeout(100 milliseconds) // Timeout: 100ms

  var subscribers = MutableList[ActorRef]()
  var log = MutableList[ResponseMessage]()
  var censorshipCache = HashMap[String, ResponseMessage]()
  var targets = Set[ActorRef]()

  def receive = {
    case DeliveryCommand.NewClient(subscriber) => // added a new client
      context.watch(subscriber)
      subscribers += subscriber
    case DeliveryCommand.NewActor(target) =>
      targets += target
    case msg: ResponseMessage => // message from actors
      //println(msg.toJson)
      log += msg
      dispatch(msg.toJson)
    case RequestProtocol.DumpLog =>
      dispatch(DumpLog(log.toList).toJson)
    /* !! FIX THOSE THREE PROCS BELOW LATER !! */
    case RequestProtocol.Rollback(time) =>
      val futures = targets.map { target =>
        (target ? RequestProtocol.Rollback(time)).mapTo[Int] // Future[String]
      }
      Future.reduce(futures)(_ + _).andThen { case Success(num) =>
        // emit resend messages
        targets.foreach(_ ! RequestProtocol.ResendMessage(time))
      }
    case msg @ RequestProtocol.AddCensorship(id, censorshipType, value) =>
      val futures = targets.map { target =>
        (target ? msg).mapTo[Int] // Future[String]
      }
      Future.reduce(futures)(_ + _).andThen { case Success(num) =>
        val response = AddCensorship(id, censorshipType, value)
        censorshipCache(id) = response
        dispatch(response.toJson)
      }
    case msg @ RequestProtocol.RemoveCensorship(id) =>
      val futures = targets.map { target =>
        (target ? msg).mapTo[Int] // Future[String]
      }
      Future.reduce(futures)(_ + _).andThen { case Success(num) =>
        val response = RemoveCensorship(id)
        censorshipCache -= id
        dispatch(response.toJson)
      }
    /* ****************************************** */
    case RequestProtocol.ExportCensorship =>
      censorshipCache.foreach({ case (_, value) =>
        dispatch(value.toJson) } ) // emit AddCensorship
    case RequestProtocol.SelectFromPool(uid) =>
      targets.foreach { target =>
        target ! RequestProtocol.SelectFromPool(uid)
      }
    case Terminated(sub) => // left a client
      subscribers = subscribers.filterNot(_ == sub)
  }
  def dispatch(msg: String): Unit = subscribers.foreach(_ ! TextMessage(msg))
}
