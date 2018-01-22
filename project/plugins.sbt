
// AspectJ
addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0")
// Bintray
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.2")

// scripted for plugin testing
libraryDependencies <+= sbtVersion(v => "org.scala-sbt" % "scripted-plugin" % v)