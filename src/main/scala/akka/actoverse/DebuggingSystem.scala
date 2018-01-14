package akka.actoverse

import akka.actor.{ Actor, ActorSystem, Props, ActorRef}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Source, Flow, Keep }
import akka.stream.scaladsl.Sink
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.model.ws.{ TextMessage, BinaryMessage, Message }
import akka.http.scaladsl.model.{ HttpResponse, Uri, HttpRequest }
import akka.http.scaladsl.model.HttpMethods._
import com.typesafe.config.Config
import scala.concurrent.Promise

class DebuggingSystem {
  private var _dispatcher: Option[ActorRef] = None
  private var currentSystem: Option[ActorSystem] = None

  def dispatcher = _dispatcher.get

  def introduce(system: ActorSystem): Unit = {
    // Make a dependent actor system for websocket
    implicit val wsSystem = ActorSystem()
    implicit val materializer = ActorMaterializer()
    currentSystem = Some(wsSystem)

    // message dispatcher
    lazy val dispatcherRef = system.actorOf(Props(new DeliveryActor(system)), name = "__debugger")
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
