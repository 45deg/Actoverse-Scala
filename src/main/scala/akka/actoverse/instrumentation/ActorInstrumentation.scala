package akka.actoverse.instrumentation

import org.aspectj.lang.annotation._
import scala.reflect.runtime.universe._
import akka.actor.Actor
import akka.actoverse.SnapShotTaker

@Aspect
class ActorInstrumentation {
  @DeclareMixin("akka.actor.Actor+")
  def mixinSnapShotToActor(actor: Actor): SnapShotTaker = {
    new SnapShotTaker {
      private val rm = runtimeMirror(actor.getClass.getClassLoader)
      private val _im = rm.reflect(actor)

      override def im: InstanceMirror = _im
    }
  }
}

// private val rm = runtimeMirror(getClass.getClassLoader)
// private val im = rm.reflect(this)