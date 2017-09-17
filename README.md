# Actoverse for Scala

Actoverse API implementation for Scala

This library extends the [Akka Actor](http://akka.io/) system to realize captureing causal relationships and reverse debugging.
Note that this implmentation is a too experimental and there are several limitations for debugging.

## Usage

* Prepare Scala (2.11.8+) and sbt (0.13.15+).

* Add lines to `build.sbt` of your project to import the library code from GitHub.

	```scala
	lazy val root = project.in(file(".")).dependsOn(actoversePlugin)
	lazy val actoversePlugin = RootProject(uri("https://github.com/45deg/Actoverse-Scala.git"))
	```

* Add the settings of the WebSocket handler to `applications.conf` of your project.

	```
	actoverse.wshandler.hostname = "localhost"
	actoverse.wshandler.port = 3000
	```

* Modify your Akka code like below for tracking the messages.

   1. Import Actoverse.

       ```scala
       import actoverse._
       ```

	1. Mix-in `DebuggingSupporter` to `Actor`.

	    ```scala
	    class Hoge extends Actor with DebuggingSupporter
	    // <= class Hoge extends Actor
	    ```

	2. Replace `!` (sends a message to an actor) with `!+`.

	    ```scala
	    fooActor !+ "message" // <= fooActor ! "message"
	    ```

	3. Add annotations `@State` to the variables which can be changed while the actor receives a message.

		```scala
		@State var count: Int = 0
		```

	4. Load the debugging module into the Akka Actor system.

		```scala
		implicit val system = ActorSystem()
		val debuggingSystem = new DebuggingSystem // <= Added
		debuggingSystem.introduce(system) // <= Added
		```
* By adding an annotation `@Comprehensive` to the class, you can make its name shown on the debugger. (ex. Foo(a=1, b=2): `{a:1,b:2}` => `["Foo",{a:1,b:2}]`)

* See also: [An Example Repository](https://github.com/45deg/Actoverse-Scala-Demos).

## How it works

### Architecture

![arch](https://user-images.githubusercontent.com/7984294/27071388-b75ee24e-5057-11e7-898a-00e2fcb4abc9.png)

- `DebuggingSystem` controls actors. It commits a command from `WebSocketHandler` to actors and reports their states to the handler.
- `WebSocketHandler` performs a mediator between the target system and the debugger UI.
- Incoming/outgoing messages are trapped by `DebuggingSupporter`s, which are accessories to actors.

### Message Enveloping

The debugger attaches an additional information (internally, called `Envelope`) to all the messages sent by actors with `!+`. It includes sender and receiver's Actor pathes, Lamport timestamps, and auto-generated UUIDs.

On the other hand, the `DebuggingSupporter` of an receiving actor opens an envelope and delivers the original message to the actor. The idea of interception comes from [Receive Pipeline Pattern](http://doc.akka.io/docs/akka/2.4-M1/contrib/receive-pipeline.html)

The figure below provides an overview of processing incoming messages (envelopes).

![flowchart](https://user-images.githubusercontent.com/7984294/27072620-f2eef85e-505b-11e7-8d5e-c0a143a13bdb.png)

### Snapshot

`DebuggingSupporter` stores the state (variables) of the actor itself every time when an actor finishes processing an incoming message, that is, finishes `receive`. Currently, you must specify the variables to be captured by adding `@State` to them.

## Limitations

This implementation does not cover several functions of Akka or Scala, such as:

- To read/write external files or networks. The debugger cannot capture these modifications and restore a previous state of them.
- To deal with stopping and restarting actors. Because of Akka Actor's lifecycle, it is impossible to regenerate an actor with the same reference as the previous actor, which stopped manually. Therefore, restoring the system to the former condition is difficult.
- To hold the `@State` variables which cannot be copied or duplicated. This framework copies and stores `@State` variables into the list. For the same reason, inconsistencies may occur by adding `@State` to a reference variable of another object which can be modified from the outside of the Actor system.