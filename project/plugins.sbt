
// AspectJ
addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0")

// scripted for plugin testing
libraryDependencies <+= sbtVersion(v => "org.scala-sbt" % "scripted-plugin" % v)