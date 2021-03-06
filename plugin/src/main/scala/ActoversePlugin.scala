package akka.actoverse.sbt

import com.lightbend.sbt.SbtAspectj
import sbt._
import sbt.Keys._

object ActoversePlugin extends AutoPlugin {

  override def requires: Plugins = SbtAspectj

  import SbtAspectj.autoImport._

  override def trigger = allRequirements

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    (resolvers += Resolver.bintrayRepo("45deg", "maven")) ++
    (libraryDependencies += "com.github.45deg" %% "actoverse" % "0.2.0") ++
    (fork := true) ++ (javaOptions ++= (aspectjWeaverOptions in Aspectj).value)
}