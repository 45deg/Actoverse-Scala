resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"

val akkaVersion = "2.5.8"

lazy val buildSettings = Seq(
  organization := "com.example",
  scalaVersion := "2.11.8",
  version      := "0.1.0-SNAPSHOT",
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % "10.0.11",
    "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "net.liftweb" %% "lift-json" % "2.6.2",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value
  )
)

lazy val actoverse = (project in file("."))
  .settings(buildSettings)
  .enablePlugins(SbtAspectj)
  .settings(
    fork := true,
    javaOptions ++= (aspectjWeaverOptions in Aspectj).value
  )
