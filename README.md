# Actoverse for Scala

Actoverse API implementation for Scala

This library extends [Akka Actor](http://akka.io/) system to realize captureing causal relationships and reverse debugging. 
Note that this is a too experimental implmentation and there are some limitations about the system to perform them.

## Usage

* Prepare Scala (2.11.8+) and sbt (0.13.15+) environment.

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

* Modify your Akka code like below to track the messages.
   
   1. Import Actoverse
       
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
	    
	3. Add annotations `@State` to variables that will be possibly changed while the actor receives a message.

		```scala
		@State var count: Int = 0
		```

	4. Load the debugging module into the Akka Actor system.
	
		```scala
		implicit val system = ActorSystem()
		val debuggingSystem = new DebuggingSystem // <= Added
		debuggingSystem.introduce(system) // <= Added
		```

* See also [an example repository](https://github.com/45deg/Actoverse-Scala-Demos) and limitations.

## How it works

### Architecture

![arch](https://user-images.githubusercontent.com/7984294/27071388-b75ee24e-5057-11e7-898a-00e2fcb4abc9.png)

- `DebuggingSystem` control all actors. It commits a command issued by `WebSocketHandler` to actors and reports their states to the handler.
- `WebSocketHandler` performs a mediator between the target system and the debugger UI. To communicate with it, this server parse JSON strings converts Scala object to the JSON format. 
- Incoming/outgoing messages are trapped by `DebuggingSupporter`, which is a "parasite" on actors.

### Message Enveloping

All the messages sent by actors with `!+` are attached an additional information (internally, called `Envelope`) that includes sender and receiver's Actor pathes, Lamport timestamps, and auto-generated UUID.

In contrast, the `DebuggingSupporter` of an receiving actor open an envelope and deliver the original message in it to the actor. The idea of interception comes from [Receive Pipeline Pattern](http://doc.akka.io/docs/akka/2.4-M1/contrib/receive-pipeline.html)

The figure below provides an overview of processing incoming messages (envelopes).

![flowchart](https://user-images.githubusercontent.com/7984294/27072620-f2eef85e-505b-11e7-8d5e-c0a143a13bdb.png)

## Limitations

The implementation does not cover some functions of Akka and Scala, such as:

- To read/write external files or networks. The debugger cannot capture these modifications and restore a previous state of them.
- To deal with stopping and restarting actors. Because of Akka Actor's lifecycle, it is impossible to regenerate an actor with the same reference as the actor which stopped manually. Therefore, restoring the system to the former condition is difficult.
- To hold the `@State` variables which can't be copied or duplicated. This library copies and stores `@State` variables into the list. By the same reason, an object that has some references of variables that can be modified from the outside of the Actor system may performs wrongly.
