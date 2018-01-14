package akka.actoverse.instrumentation

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation._
import akka.actoverse.DebuggingSystem
import akka.actor.ActorSystem

@Aspect
class ActorSystemInstrumentation {
  @After("execution(* akka.actor.ActorSystemImpl.start(..)) && !cflow(execution(void akka.actoverse.DebuggingSystem.introduce(*))) && this(system)")
  def captureSystemCreation(system: ActorSystem): Unit = {
    // piggyback
    val debuggingSystem = new DebuggingSystem
    debuggingSystem.introduce(system)
    system.registerOnTermination(() => debuggingSystem.terminate())
  }

}
