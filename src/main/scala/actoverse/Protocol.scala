package actoverse

import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import scala.collection.immutable.{Map, List}
import akka.actor.ActorPath

object ResponseProtocol {

  sealed trait ResponseMessage {
    val timestamp: Long
    val path: ActorPath
    def toJsonAst()(implicit formats: Formats): JsonAST.JValue
    def toJson(): String = {
      implicit val formats = DefaultFormats + new ComprehensiveSerializer
      compact(render(toJsonAst()))
    }
  }

  case class MessageBody(sender:ActorPath, target: ActorPath, data: Any, timestamp: Long, uid: String) {
    def toJsonAst()(implicit formats: Formats) =
      ("sender" -> sender.name) ~
      ("target" -> target.name) ~
      ("data" -> Extraction.decompose(data)) ~
      ("timestamp" -> timestamp) ~
      ("uid" -> uid)
  }

  case class SendMessage(body: MessageBody, timestamp: Long, path:ActorPath) extends ResponseMessage {
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "SEND_MESSAGE") ~
      ("body" -> body.toJsonAst) ~
      ("timestamp" -> timestamp) ~
      ("name" -> path.name)
  }
  case class ReceivedMessage(body: MessageBody, timestamp: Long, path:ActorPath) extends ResponseMessage {
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "MESSAGE_RECEIVED") ~
      ("body" -> body.toJsonAst) ~
      ("timestamp" -> timestamp) ~
      ("name" -> path.name)
  }
  case class ActorUpdated(state: Map[String, Any], timestamp: Long, path: ActorPath) extends ResponseMessage {
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "ACTOR_UPDATED") ~
      ("body" -> Extraction.decompose(state)) ~
      ("name" -> path.name) ~
      ("timestamp" -> timestamp)
  }
  case class ActorReplaced(state: Map[String, Any], timestamp: Long, path: ActorPath) extends ResponseMessage {
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "ACTOR_REPLACED") ~
      ("body" -> ("state" -> Extraction.decompose(state))) ~
      ("name" -> path.name) ~
      ("timestamp" -> timestamp)
  }

  case class ActorInfo(kind: String, state: Map[String, Any])
  case class ActorCreated(body: ActorInfo, timestamp: Long, path: ActorPath) extends ResponseMessage {
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "ACTOR_CREATED") ~
      ("body" -> Extraction.decompose(body)) ~
      ("name" -> path.name) ~
      ("timestamp" -> timestamp)
  }

  case class DumpLog(log: List[ResponseMessage]) extends ResponseMessage {
    val timestamp = -1L
    val path = null
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "DUMP_LOG") ~
      ("body" -> JArray(log.map(_.toJsonAst)))
  }

  case class AddCensorship(id: String, censorshipType: String, value: JsonAST.JValue) extends ResponseMessage {
    val timestamp = -1L
    val path = null
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "ADD_CENSORSHIP") ~
      ("body" ->
        ("type" -> censorshipType) ~
        ("value" -> Extraction.decompose(value))
      ) ~
      ("id" -> id)
  }

  case class RemoveCensorship(id: String) extends ResponseMessage {
    val timestamp = -1L
    val path = null
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "REMOVE_CENSORSHIP") ~
      ("id" -> id)
  }

  case class AddPool(body: MessageBody, timestamp: Long, path:ActorPath) extends ResponseMessage{
    def toJsonAst()(implicit formats: Formats) =
      ("event" -> "POOL_ADD") ~
      ("body" -> body.toJsonAst) ~
      ("timestamp" -> timestamp) ~
      ("name" -> path.name)
  }
}

object RequestProtocol {
  sealed trait RequestMessage

  case object DumpLog extends RequestMessage
  case class Rollback(time: Long) extends RequestMessage
  case class ResendMessage(time: Long) extends RequestMessage
  case class AddCensorship(id: String, censorshipType: String, value: JsonAST.JValue) extends RequestMessage
  case class RemoveCensorship(id: String) extends RequestMessage
  case object ExportCensorship extends RequestMessage
  case object InvalidMessage extends RequestMessage
  case class SelectFromPool(id: String) extends RequestMessage

  def fromJson(json: String): RequestMessage = {
    val ast = parse(json)
    val JString(msgType) = ast \ "type"
    //println(s"ARRIVING $json")
    msgType match {
      case "dump_log" => DumpLog
      case "rollback" =>
        val JInt(time) = ast \ "time"
        Rollback(time.longValue)
      case "add_filter" =>
        val id = java.util.UUID.randomUUID.toString
        val JString(censorshipType) = ast \ "body" \ "type"
        val value = ast \ "body" \ "value"
        AddCensorship(id, censorshipType, value)
      case "remove_filter" =>
        val JString(id) = ast \ "id"
        RemoveCensorship(id)
      case "export_filters" =>
        ExportCensorship
      case "select" =>
        val JString(id) = ast \ "uid"
        SelectFromPool(id)

      case _ => InvalidMessage
    }
  }
}
