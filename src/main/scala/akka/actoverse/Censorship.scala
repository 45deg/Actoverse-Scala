package akka.actoverse

import akka.actor._
import net.liftweb.json._

object Censorship {
  def fromJson(censorshipType: String, jsonAst: JValue): Censorship = censorshipType match {
    case "sender_name" =>
      val JString(name) = jsonAst
      SenderCensorship(name)
    case "target_name" =>
      val JString(name) = jsonAst
      TargetCensorship(name)
    case "partial_match" =>
      PartialMatch(jsonAst)
    case "perfectMatch" =>
      PerfectMatch(jsonAst)
    case "any" =>
      AnyCensorship
  }
}

trait Censorship {
  def check(sender: ActorPath, target: ActorPath, data: Any): Boolean
}

case class SenderCensorship(name: String) extends Censorship {
  def check(sender: ActorPath, target: ActorPath, data: Any): Boolean = {
    sender.name == name
  }
}
case class TargetCensorship(name: String) extends Censorship {
  def check(sender: ActorPath, target: ActorPath, data: Any): Boolean = {
    target.name == name
  }
}

case class PartialMatch(jsonAst: JValue) extends Censorship {
  def check(sender: ActorPath, target: ActorPath, data: Any): Boolean = {
    implicit val formats = DefaultFormats + new ComprehensiveSerializer
    val Diff(changed, added, _) = Extraction.decompose(data) diff jsonAst
    changed == JNothing && added == JNothing
  }
}

case class PerfectMatch(jsonAst: JValue) extends Censorship {
  def check(sender: ActorPath, target: ActorPath, data: Any): Boolean = {
    implicit val formats = DefaultFormats + new ComprehensiveSerializer
    val Diff(changed, added, deleted) = Extraction.decompose(data) diff jsonAst
    changed == JNothing && added == JNothing && deleted == JNothing
  }
}

case object AnyCensorship extends Censorship {
  def check(sender: ActorPath, target: ActorPath, data: Any): Boolean = true
}
