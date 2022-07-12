package part2_remoting

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorSystem, Identify, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

object RemoteActors extends App {

  val localSystem =
    ActorSystem("LocalSystem", ConfigFactory.load("part2_remoting/remoteActors.conf"))

  val localSimpleActor = localSystem.actorOf(Props[SimpleActor], "simpleLocalActor")

  localSimpleActor ! "Hello local actor"

  // send a message to remote simple actor
  // method 1: actor selection
  val remoteActorSelection =
    localSystem.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteSimpleActor")
  remoteActorSelection ! "hello from local jvm"

  // method 2: resolve the actor selection to an actor ref
  implicit val timeout: Timeout = Timeout(3.seconds)
  import localSystem.dispatcher

  val remoteActorRefFuture = remoteActorSelection.resolveOne()
  remoteActorRefFuture.onComplete {
    case Failure(exception) => println(s"Failed to resolve $exception")
    case Success(actorRef)  => actorRef ! "I have resolved you in a future!"
  }

  // Method 3: actor identification via messages
  /*
   -  actor resolver will ask for an actor selection from the local actor system
   -  actor resolver will send an Identity(42) to the actor selection
   -  the remote actor will automatically  respond with actoridentity(42, ref)
   -  the actor resolver is free to use remote actor ref
   */
  class ActorResolver extends Actor with ActorLogging {
    override def preStart(): Unit = {
      val selection =
        context.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteSimpleActor")
      selection ! Identify(42)
      super.preStart()
    }
    override def receive: Receive = { case ActorIdentity(42, Some(actorRef)) =>
      actorRef ! "Thank for identify yourself"
    }
  }

  localSystem.actorOf(Props[ActorResolver], "localActorResolver")
}

object RemoteActors_remote extends App {
  val remoteSystem = ActorSystem(
    "RemoteSystem",
    ConfigFactory.load("part2_remoting/remoteActors.conf").getConfig("remoteSystem")
  )

  val remoteSimpleActor = remoteSystem.actorOf(Props[SimpleActor], "remoteSimpleActor")

  remoteSimpleActor ! "Hello remote actor"
}
