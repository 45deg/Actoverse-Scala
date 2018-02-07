package akka.actoverse

import scala.concurrent.duration._
import akka.actor._
import akka.testkit.TestProbe
import org.scalatest.FlatSpec

class Dummy extends Actor {
  val receive: Receive = {
    case "hello" => sender ! "world"
  }
}

class BasicSpec extends FlatSpec {
  it should "send/receive messages" in {
    implicit val system = ActorSystem()
    try {
      val probe = TestProbe()
      val dummy = system.actorOf(Props[Dummy], name="dummy")
      dummy.tell("hello", probe.ref)
      probe.expectMsg(1000 millis, "world")
    } finally {
      system.terminate()
    }
  }
}
