package institute.besna.solver

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.Success
import akka.Done
import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.grpc.GrpcClientSettings
import akka.stream.scaladsl.Source
import org.slf4j.Logger

object SolverClient {

  def main(args: Array[String]): Unit = {
    implicit val sys: ActorSystem[_] = ActorSystem(Behaviors.empty, "SolverClient")
    implicit val ec: ExecutionContext = sys.executionContext
    val log: Logger = sys.log

    val client = SolverServiceClient(GrpcClientSettings.fromConfig("solver.SolverService"))

    val names =
      if (args.isEmpty) List("Alice", "Bob")
      else args.toList

    names.foreach(singleRequestResponse)

    if (args.nonEmpty)
      names.foreach(streamingBroadcast)

    def singleRequestResponse(name: String): Unit = {
      log.info(s"Performing request: $name")
      val response = client.analyzeOnUnaryRPC(SolverRequest(apiName="API1", name=name))
      response.onComplete {
        case Success(msg: SolverResponse) =>
          log.info(msg.toString)
        case Failure(t: Throwable) =>
          log.info(s"Error: $t")
      }
    }

    def streamingBroadcast(name: String): Unit = {
      log.info(s"Performing streaming requests: $name")

      val requestStream: Source[SolverRequest, NotUsed] =
        Source
          .tick(1.second, 1.second, "tick")
          .zipWithIndex
          .map { case (_, i) => i }
          .map(i => SolverRequest(s"$name-$i"))
          .mapMaterializedValue(_ => NotUsed)

      val responseStream: Source[SolverResponse, NotUsed] = client.analyzeOnBidirectionalStreamingRPC(requestStream)
      val done: Future[Done] =
        responseStream.runForeach(response => log.info(s"$name got streaming reply: ${response.response.reply.get.text}"))

      done.onComplete {
        case Success(_) =>
          log.info("streamingBroadcast done")
        case Failure(t: Throwable) =>
          log.info(s"Error streamingBroadcast: $t")
      }
    }
  }

}