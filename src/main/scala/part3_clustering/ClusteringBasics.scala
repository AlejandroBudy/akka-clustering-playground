package part3_clustering

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory

class ClusterSubscriber extends Actor with ActorLogging {
  private val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    )
  }

  override def postStop(): Unit = cluster.unsubscribe(self)

  override def receive: Receive = {
    case MemberJoined(member) =>
      log.info(s"New member in town ${member.address}")
    case MemberUp(member) =>
      log.info(s"Let's say welcome to the newest member: ${member.address}")
    case MemberRemoved(member, previous) =>
      log.info(s"Poor ${member.address}, it was removed from $previous")
    case UnreachableMember(member) =>
      log.info(s"member ${member.address} is unreachable")
    case m: MemberEvent =>
      log.info(s"Other $m")
  }
}
object ClusteringBasics extends App {

  def startCluster(ports: List[Int]): Unit = {
    ports.foreach { port =>
      val config = ConfigFactory
        .parseString(
          s"""
           |akka.remote.artery.canonical.port=$port
           |""".stripMargin
        )
        .withFallback(ConfigFactory.load("part3_clustering/clusteringBasics.conf"))
      val system = ActorSystem("AlexCluster", config) // all actors systems must have same name
      system.actorOf(Props[ClusterSubscriber], "ClusterSubscriber")
    }
  }

  startCluster(List(2551, 2552, 0))
}

object ClusteringBasics_ManualRegistration extends App {
  val system = ActorSystem(
    "AlexCluster",
    ConfigFactory.load("part3_clustering/clusteringBasics.conf").getConfig("manualRegistration")
  )

  val cluster = Cluster(system)

  cluster.joinSeedNodes(List(
    Address("akka", "AlexCluster", "localhost", 2551),
    Address("akka", "AlexCluster", "localhost", 2552)
  ))

  system.actorOf(Props[ClusterSubscriber], "ClusterSubscriber")

}
