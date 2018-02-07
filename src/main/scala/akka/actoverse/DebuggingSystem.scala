package akka.actoverse

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws.{Message, TextMessage, UpgradeToWebSocket}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, Uri}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.ConfigFactory

object DebuggingSystem {
  lazy val wsSystem = ActorSystem(ConfigFactory.load().getString("actoverse.debugger-system-name"))
}

class DebuggingSystem {
  private var _dispatcher: Option[ActorRef] = None
  private var currentSystem: Option[ActorSystem] = None

  def dispatcher = _dispatcher.get

  def introduce(system: ActorSystem): Unit = {
    // Make a dependent actor system for websocket
    implicit val wsSystem = DebuggingSystem.wsSystem
    implicit val materializer = ActorMaterializer()
    currentSystem = Some(wsSystem)

    // message dispatcher
    lazy val dispatcherRef = system.actorOf(Props(new DeliveryActor(system)), name = system.settings.config.getString("actoverse.debugger-actor-name"))
    // outgoing stream for websocket
    val source = Source.actorRef[Message](1000, akka.stream.OverflowStrategy.fail)
                       .mapMaterializedValue(dispatcherRef ! DeliveryCommand.NewClient(_))
    // incoming stream for websocket
    val sink = Flow[Message]
    .collect { case TextMessage.Strict(text) => text }
    .to(Sink.foreach(dispatcherRef ! RequestProtocol.fromJson(_)))

    // HTTP Endpoint
    val requestHandler: HttpRequest => HttpResponse = {
      // Request for /
      case req @ HttpRequest(GET, Uri.Path("/"), _, _, _) =>
        req.header[UpgradeToWebSocket] match {
          case Some(upgrade) => upgrade.handleMessagesWithSinkSource(sink, source)
          case None          => HttpResponse(400, entity = "Not a valid websocket request!")
        }
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        HttpResponse(404, entity = "Unknown resource!")
    }

    // Listen
    val hostname = wsSystem.settings.config.getString("actoverse.wshandler.hostname")
    val port = wsSystem.settings.config.getInt("actoverse.wshandler.port")
    val bindingFuture = Http().bindAndHandleSync(requestHandler, interface = hostname, port = port)

    _dispatcher = Some(dispatcherRef)
  }

  def terminate() = {
    currentSystem.map(s => s.terminate())
  }
}
