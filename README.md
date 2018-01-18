# Actoverse for Scala (Akka)

Actoverse API implementation for Akka Actor Framework

This library extends the [Akka Actor](http://akka.io/) system to realize captureing causal relationships and reverse debugging.
Note that this implmentation is too experimental and there are several limitations for debugging.

## Usage

*Important: After ver 0.2.0, you don't have to rewrite source codes by introducing AspectJ.*

Create file `project/plugins.sbt` in your project with:

```
addSbtPlugin("com.github.45deg" %% "actoverse-sbt" % "0.2.0-SNAPSHOT")
```

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

### AspectJ

AspectJ (a framework for [Aspect-oriented programming](https://en.wikipedia.org/wiki/Aspect-oriented_programming)) enables 
the debugger to hook Actors' behaviors such as sending or receiving messages without modifying source codes. In this work, I used [akka-viz](https://github.com/blstream/akka-viz) and [kamon-akka](https://github.com/kamon-io/kamon-akka) as reference and they were very helpful.

## Limitations

This implementation does not cover several functions of Akka or Scala, such as:

- To read/write external files or networks. The debugger cannot capture these modifications and restore a previous state of them.
- To deal with stopping and restarting actors. Because of Akka Actor's lifecycle, it is impossible to regenerate an actor with the same reference as the previous actor, which stopped manually. Therefore, restoring the system to the former condition is difficult.
- To variables which cannot be copied or duplicated. This framework copies and stores variables into a list. For the same reason, inconsistencies may occur when there is a reference variable of another object which can be modified from the outside of the Actor system.
