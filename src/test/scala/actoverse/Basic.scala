package akka.actoverse

import scala.concurrent._
import scala.concurrent.duration._
import akka.actor._
import actoverse._
import akka.testkit.TestProbe
import org.scalatest.FlatSpec

class Dummy extends Actor with DebuggingSupporter {
  val receive: Receive = {
    case "hello" => sender ! "world"
  }
}

class BasicSpec extends FlatSpec {
  it should "send/receive messages" in {
    implicit val system = ActorSystem()
    try {
      val debuggingSystem = new DebuggingSystem
      debuggingSystem.introduce(system)
      val probe = TestProbe()
      val dummy = system.actorOf(Props[Dummy], name="dummy")
      dummy.tell("hello", probe.ref)
      probe.expectMsg(1000 millis, "world")
    } finally {
      system.shutdown()
    }
  }
}
