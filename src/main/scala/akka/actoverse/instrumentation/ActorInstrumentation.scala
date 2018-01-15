package akka.actoverse.instrumentation

import org.aspectj.lang.annotation._
import com.typesafe.config.ConfigFactory

import scala.reflect.runtime.universe._
import akka.actor._
import akka.actoverse.RequestProtocol.RequestMessage
import akka.actoverse.ResponseProtocol.ResponseMessage
import akka.actoverse.{DeliveryCommand, SnapShotTaker}

import scala.util.matching.Regex

@Aspect
class ActorInstrumentation {

  private val config = ConfigFactory.load(this.getClass.getClassLoader)
  private val targetRegex = new Regex("^" + config.getString("actoverse.target-actorpath") + "$")
  private val debuggerPath = config.getString("actoverse.debugger-actor-name")

  @DeclareMixin("akka.actor.Actor+")
  def mixinSnapShotToActor(actor: Actor): SnapShotTaker = {
    new SnapShotTaker {
      private val rm = runtimeMirror(actor.getClass.getClassLoader)
      private val _im = rm.reflect(actor)

      override def im: InstanceMirror = _im
    }
  }

  @After("execution(* akka.actor.ScalaActorRef+.$bang(..)) && this(self) && args(message, sender)")
  def afterBang(self: ScalaActorRef, message: Any, sender: ActorRef) = {
    if(targetRegex.pattern.matcher(self.path.toString).matches() &&
       sender != null && targetRegex.pattern.matcher(sender.path.toString).matches() &&
       self.path.name != debuggerPath) {
      message match {
        case _: ResponseMessage => ()
        case _: RequestMessage => ()
        case _: DeliveryCommand => ()
        case _ =>
          // println(s"$sender sends $message to $self")
      }
    }
  }
}

// private val rm = runtimeMirror(getClass.getClassLoader)
// private val im = rm.reflect(this)