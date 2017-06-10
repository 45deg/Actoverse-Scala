name := "Actoverse for Akka"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT",
  "com.typesafe.akka" %% "akka-http-core" % "10.0.0",
  "com.typesafe.akka" %% "akka-contrib" % "2.4-SNAPSHOT",
  "com.typesafe.akka" %% "akka-testkit" % "2.4-SNAPSHOT" % "test",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test",
  "net.liftweb" %% "lift-json" % "2.6.2"
)

resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
