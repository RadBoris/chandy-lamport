package chandylamport;

import akka.actor.{ Actor, ActorSystem, ActorSelection, Props, ActorRef }
import scala.collection.mutable

case class Neighbor(id: Int, path: String)
case class Start(id: Int, nextNeighbor: Neighbor, previousNeighbor: Neighbor)
case class ACK(number: Int)
case class MARKER(number: Int)
case object MESSAGEONE
case object MESSAGETWO

class TokenRing extends Actor {
  var actor:Int = 0;
  var counterOne:Int = 0;
  var counterTwo:Int = 0;
  var nextActorPath = "";

  def incrementCounterOne = { counterOne += 1; counterOne }
  def incrementCounterTwo = { counterTwo += 1; counterTwo }

  var AckCount:Int = 0;

  var nextNode:Neighbor = new Neighbor(0, "")
  var previousNode:Neighbor = new Neighbor(0, "")
  var recordingState:Boolean = false
  var toNotify:String = ""

  def receive = {
    case MESSAGEONE =>
      this.incrementCounterOne
      Thread.sleep(2000)
      println(" Actor " + actor + " received " + MESSAGEONE.toString() + " number of times : " + counterOne)
      val next = context.actorSelection(nextActorPath)
      next ! MESSAGEONE
    case MESSAGETWO =>
      this.incrementCounterTwo
      Thread.sleep(2000)
      println(" Actor " + actor + " received " + MESSAGETWO.toString() + " number of times : " + counterTwo + " from "  + sender.path.toString())
      val next = context.actorSelection(nextActorPath)
      next ! MESSAGETWO

    case MARKER(number) =>
      if(!recordingState){
        println("Actor " + actor + " received Marker from Actor " + number + ", started recording state.")
        recordingState = true
        toNotify = sender.path.toString
        if(number != nextNode.id){
          val next = context.actorSelection(nextNode.path)
          next ! MARKER(actor)
          AckCount += 1
        }
        if(number != previousNode.id){
          val previous = context.actorSelection(previousNode.path)
          previous ! MARKER(actor)
          AckCount += 1
        }
      } else{
        println("Actor " + actor + " currently recording state, marker received from " + number + ".")
        sender ! ACK(actor)
      }

    case ACK(number) =>
      if(recordingState){
        println("Actor " + actor + " received Ack from Actor " + number.toString)
        AckCount -= 1
        if(AckCount <= 0){
          println("Markers Received, State is " + self + ": Actor " + actor)
          AckCount = 0
          recordingState = false
          val firstSender = context.actorSelection(toNotify)
          firstSender ! ACK(actor)
        }
      }
    case Start(id, nextNeighbor, previousNeighbor) =>
      actor = id
      nextNode  = nextSib
      previousNode = previousSib
  }
}

object Server extends App {
  val system = ActorSystem("TokenRing")
  val first = system.actorOf(Props[TokenRing], name = "first")
  println(first.path)

  val second = system.actorOf(Props[TokenRing], name = "second")
  println(second.path)

  val third = system.actorOf(Props[TokenRing], name = "third")
  println(third.path)

  val firstNeighborC = new Neighbor(1, first.path.toString)
  val secondNeighborC = new Neighbor(2, second.path.toString)
  val thirdNeighborC = new Neighbor(3, third.path.toString)

  val firstNeighborCC = new Neighbor(1, third.path.toString)
  val secondNeighborCC = new Neighbor(3, first.path.toString)
  val thirdNeighborCC = new Neighbor(2, second.path.toString)

  first ! Start(1, secondNeighborC, thirdNeighborC)
  second ! Start(2, firstNeighborC, thirdNeighborC)
  third ! Start(3, secondNeighborC, firstNeighborC)
  first ! MESSAGEONE

  first ! Start(1, secondNeighborCC, thirdNeighborCC)
  third ! Start(3, firstNeighborCC, secondNeighborCC)
  second ! Start(2, thirdNeighborCC, firstNeighborCC)
  first ! MESSAGETWO

  println("Server Ready")

  var i = 0
  while(i <= 2) {
    Thread.sleep(2000)
    second ! MARKER(0)
    third ! MARKER(0)
    i = i + 1
    Thread.sleep(2000)
    third ! MARKER(0)
    i = i + 1
  }
}
