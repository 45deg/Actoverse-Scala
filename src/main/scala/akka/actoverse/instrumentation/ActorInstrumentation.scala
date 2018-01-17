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
    /*new DebuggingInterceptor {
      override def actor: Actor = _actor
    }*/
    val actorPath: ActorPath = _actor.self.path
    if(targetRegex.pattern.matcher(actorPath.toString).matches() && actorPath.name != debuggerPath) {
      new ConcreteDebuggingInterceptor(_actor)
    } else {
      DebuggingInterceptor.Null
    }
  }

  @After("execution(* akka.actor.Actor.preStart(..)) && this(self)")
  def beforeStart(self: Actor): Unit = {
    self.asInstanceOf[DebuggingInterceptor].beforeStart()
  }

  @Around("execution(* akka.actor.Actor.aroundReceive(..)) && this(self) && args(receive, msg)")
  def aroundReceive(jp: ProceedingJoinPoint, self: Actor, receive: Actor.Receive, msg: Any): Unit = {
    val clos : Actor.Receive = { case m => self.asInstanceOf[DebuggingInterceptor].wrapReceive(m, receive) }
    val args: Array[AnyRef] = Array(self.asInstanceOf[AnyRef], clos.asInstanceOf[AnyRef], msg.asInstanceOf[AnyRef])
    jp.proceed(args)
  }

  @Around("execution(* akka.actor.ScalaActorRef+.$bang(..)) && this(self) && args(message, sender)")
  def aroundBang(jp: ProceedingJoinPoint, self: ScalaActorRef, message: Any, sender: ActorRef): Unit = {
    if(targetRegex.pattern.matcher(self.path.toString).matches() &&
       sender != null && targetRegex.pattern.matcher(sender.path.toString).matches() &&
       self.path.name != debuggerPath && !message.isInstanceOf[MetaMessage]) {
      try {
        val actor = sender.asInstanceOf[ActorRefWithCell].underlying.asInstanceOf[ActorCell].actor
        val envelope = actor.asInstanceOf[DebuggingInterceptor].wrapEnvelope(self, message, sender)
        val args: Array[AnyRef] = Array(self.asInstanceOf[AnyRef],
          envelope.asInstanceOf[AnyRef], sender.asInstanceOf[AnyRef])
        // println(s"!pass $envelope")
        jp.proceed(args)
      } catch {
        case e: ClassCastException =>
          jp.proceed()
      }
    } else {
      jp.proceed()
    }
  }
}

// private val rm = runtimeMirror(getClass.getClassLoader)
// private val im = rm.reflect(this)