package akka.actoverse

import scala.reflect.runtime.universe._

trait SnapShotTaker {
  import scala.collection._
  private var stateSnapshots = mutable.Map[Long, immutable.Map[TermSymbol, Any]]()
  private val rm = runtimeMirror(getClass.getClassLoader)
  private val im = rm.reflect(this)

  def takeStateSnapshot(serialNr: Long): immutable.Map[String, Any] = {
    val targetFields = im.symbol.selfType.members
                        .collect { case s: TermSymbol => s }
                        .filter(f =>
                          f.annotations.exists(_.tree.tpe =:= typeOf[State]))
    stateSnapshots(serialNr) = targetFields.map { field =>
      (field, im.reflectField(field).get)
    }.toMap

     // convert to Map[String, Any]
    stateSnapshots(serialNr).map({ case (field, value) =>
      (field.name.toString, value)
    })
  }

  def recoverStateUntil(timestamp: Long): immutable.Map[String, Any] = {
    // filterKeys does not return mutable one
    stateSnapshots = stateSnapshots.filter(_._1 < timestamp)
    val latestState = stateSnapshots(stateSnapshots.keys.max)
    latestState.foreach { case (field, value) =>
      im.reflectField(field).set(value) }

     // convert to Map[String, Any]
    latestState.map({ case (field, value) =>
      (field.name.toString, value)
    })
  }

}
