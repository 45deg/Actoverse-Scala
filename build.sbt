val akkaVersion = "2.5.8"

lazy val buildSettings = Seq(
  name         := "actoverse",
  organization := "com.github.45deg",
  description  := "Actoverse API for Akka",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.11.8"),
  version      := "0.2.0-SNAPSHOT"
)

lazy val actoverse = (project in file("."))
  .settings(buildSettings)
  .enablePlugins(SbtAspectj)
  .settings(
    aspectjCompileOnly in Aspectj := true,
    products in Compile ++= (products in Aspectj).value,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
      "com.typesafe.akka" %% "akka-http" % "10.0.11",
      "org.scalatest" %% "scalatest" % "3.0.1" % "test",
      "net.liftweb" %% "lift-json" % "2.6.2",
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )
  .aggregate(plugin)

lazy val plugin = (project in file("plugin"))
  .enablePlugins(SbtAspectj)
  .settings(buildSettings)
  .settings(
    name := "actoverse-sbt",
    sbtPlugin := true,
    scalaVersion := "2.10.6",
    crossScalaVersions := Seq("2.10.6"),
    addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0"),
    // scripted-plugin
    ScriptedPlugin.scriptedSettings,
    scriptedBufferLog  := false,
    scriptedLaunchOpts ++= Seq(
      "-Dproject.version=" + version.value,
      "-Dscala.version=" + scalaVersion.value
    ),
    watchSources ++= (sourceDirectory map { path => (path ** "*").get }).value
  )
