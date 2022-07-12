package part3_clustering

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Address, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._
import akka.pattern.pipe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{Duration, DurationInt}
import scala.util.Random

object ClusteringExampleDomain {

  case class ProcessFile(filename: String)
  case class ProcessLine(line: String, aggregator: ActorRef)
  case class ProcessLineResult(count: Int)

}

class Master extends Actor with ActorLogging {

  import ClusteringExampleDomain._
  import context.dispatcher

  implicit val timeout: Timeout = Timeout(3.seconds)

  private val cluster: Cluster = Cluster(context.system)
  var workers: Map[Address, ActorRef] = Map()
  var pendingRemoval: Map[Address, ActorRef] = Map()

  override def preStart(): Unit = {
    cluster.subscribe(
      self,
      initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent],
      classOf[UnreachableMember]
    )
  }

  override def postStop(): Unit =
    cluster.unsubscribe(self)

  override def receive: Receive =
    handleClusterEvents.orElse(handleJob).orElse(handleWorkerRegistration)

  def handleClusterEvents: Receive = {
    case MemberUp(member) if member.hasRole("worker") =>
      log.info(s"Member ${member.address} is up")
      if (pendingRemoval.contains(member.address)) {
        pendingRemoval = pendingRemoval - member.address
      } else {
        val workerSelection = context.actorSelection(s"${member.address}/user/worker")
        workerSelection.resolveOne().map(ref => (member.address, ref)).pipeTo(self)
      }
    case UnreachableMember(member) =>
      log.info(s"Member ${member.address} unreachable")

      val workerOptions = workers.get(member.address)
      workerOptions.foreach { ref =>
        pendingRemoval = pendingRemoval + (member.address -> ref)
      }
    case MemberRemoved(member, previousStatus) =>
      log.info(s"Member ${member.address} removed after $previousStatus")
      workers = workers - member.address
    case m: MemberEvent =>
      log.info(s"Other $m")
  }

  def handleWorkerRegistration: Receive = {
    case pair: (Address, ActorRef) =>
      log.info(s"Registering worker $pair")
      workers = workers + pair
  }

  def handleJob: Receive = {
    case ProcessFile(filename) =>
      log.info(s"About to process file $filename")
      val aggregator = context.actorOf(Props[Aggregator], "aggregator")
      scala.io.Source.fromFile(filename).getLines().foreach { line =>
        val workerIndex = Random.nextInt((workers -- pendingRemoval.keys).size)
        val worker: ActorRef = (workers -- pendingRemoval.keys).values.toSeq(workerIndex)
        worker ! ProcessLine(line, aggregator)
        Thread.sleep(10)
      }
  }
}

class Aggregator extends Actor with ActorLogging {
  import ClusteringExampleDomain._

  context.setReceiveTimeout(3.seconds)
  override def receive: Receive = online(0)

  def online(totalCount: Int): Receive = {
    case ProcessLineResult(count) =>
      context.become(online(totalCount + count))

    case ReceiveTimeout =>
      log.info(s"Total count: $totalCount")
      context.setReceiveTimeout(Duration.Undefined)
  }
}

class Worker extends Actor with ActorLogging {
  import ClusteringExampleDomain._
  override def receive: Receive = { case ProcessLine(line, aggregator) =>
    log.info(s"Processing: $line")
    aggregator ! ProcessLineResult(line.split(" ").length)

  }
}

object SeedNodes extends App {
  import ClusteringExampleDomain._
  def createNode(port: Int, role: String, props: Props, actorName: String) = {
    val config = ConfigFactory
      .parseString(
        s"""
         |akka.cluster.roles = ["$role"]
         |akka.remote.artery.canonical.port = $port
         |""".stripMargin
      )
      .withFallback(ConfigFactory.load("part3_clustering/clusteringExample.conf"))

    val system = ActorSystem("AlexCluster", config)
    system.actorOf(props, actorName)
  }

  val master = createNode(2551, "master", Props[Master], "master")
  createNode(2552, "worker", Props[Master], "worker")
  createNode(2553, "worker", Props[Master], "worker")
  Thread.sleep(10000)
  master ! ProcessFile("src/main/resources/txt/lipsum.txt")

}
