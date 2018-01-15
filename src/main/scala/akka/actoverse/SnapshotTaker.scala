package akka.actoverse

import scala.reflect.runtime.universe._

trait SnapShotTaker {
  import scala.collection._
  private var stateSnapshots = mutable.Map[Long, immutable.Map[TermSymbol, Any]]()

  def im: InstanceMirror

  def takeStateSnapshot(serialNr: Long): immutable.Map[String, Any] = {
    val targetFields = im.symbol.selfType.members
                        .collect { case s: TermSymbol if s.isVar => s }
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
