package akka.actoverse.instrumentation

import org.aspectj.lang.annotation._
import com.typesafe.config.ConfigFactory

import scala.reflect.runtime.universe._
import akka.actor._
import akka.actoverse.RequestProtocol.RequestMessage
import akka.actoverse.ResponseProtocol.ResponseMessage
import akka.actoverse._
import org.aspectj.lang.ProceedingJoinPoint

import scala.util.matching.Regex

@Aspect
class ActorInstrumentation {

  private val config = ConfigFactory.load(this.getClass.getClassLoader)
  private val targetRegex = new Regex("^" + config.getString("actoverse.target-actorpath") + "$")
  private val debuggerPath = config.getString("actoverse.debugger-actor-name")

  @DeclareMixin("akka.actor.Actor+")
  def mixinDebuggingSupporterToActor(_actor: Actor): DebuggingInterceptor = {
    new DebuggingInterceptorImpl {
      override def actor: Actor = _actor
    }
  }

  @After("execution(* akka.actor.Actor.preStart(..)) && this(self)")
  def beforeStart(self: Actor): Unit = {
    self match {
      case s: DebuggingInterceptorImpl => s.beforeStart()
      case _ => ()
    }
  }

  @Around("execution(* akka.actor.Actor.aroundReceive(..)) && this(self) && args(receive, msg)")
  def aroundReceive(jp: ProceedingJoinPoint, self: Actor, receive: Actor.Receive, msg: Any): Unit = {
    self match {
      case s: DebuggingInterceptorImpl =>
        val clos : Actor.Receive = { case m => s.wrapReceive(m, receive) }
        val args: Array[AnyRef] = Array(self.asInstanceOf[AnyRef], clos.asInstanceOf[AnyRef], msg.asInstanceOf[AnyRef])
        jp.proceed(args)
      case _ =>
        jp.proceed()
    }
  }

  @After("execution(* akka.actor.ScalaActorRef+.$bang(..)) && this(self) && args(message, sender)")
  def afterBang(self: ScalaActorRef, message: Any, sender: ActorRef): Unit = {
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