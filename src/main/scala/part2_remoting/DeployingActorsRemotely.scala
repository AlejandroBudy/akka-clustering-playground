package part2_remoting

import akka.actor.{ActorSystem, Address, AddressFromURIString, Deploy, Props}
import akka.remote.RemoteScope
import com.typesafe.config.ConfigFactory

object DeployingActorsRemotely_LocalApp extends App {
  val system = ActorSystem(
    "LocalActorSystem",
    ConfigFactory.load("part2_remoting/deployingActorsRemotely.conf").getConfig("localApp")
  )
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor")

  simpleActor ! "Hello remote actor"

  // programmatic remote deployment
  val remoteSystemAddress: Address = AddressFromURIString("akka://RemoteActorSystem@localhost:2552")
  val remotelyDeployActor = system.actorOf(
    Props[SimpleActor].withDeploy(
      Deploy(scope = RemoteScope(remoteSystemAddress))
    )
  )

  remotelyDeployActor ! "Hi remotely deployed actor"

}

object DeployingActorsRemotely_RemoteApp extends App {
  val system = ActorSystem(
    "RemoteActorSystem",
    ConfigFactory.load("part2_remoting/deployingActorsRemotely.conf").getConfig("remoteApp")
  )
}
