package institute.besna.solver

import akka.NotUsed
import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object HttpToGrpc {

  private def unaryResponse(response: Future[SolverResponse])
                           (implicit ec: ExecutionContext): Route = {
    onComplete(response) {
      case Success(r: SolverResponse) =>
        if (r.response.isReply) {
          val text: String = r.response.reply.map(_.text).getOrElse("")
          complete(text)
        } else if (r.response.isError) {
          val text: String = r.response.error.map(_.errorMessage).getOrElse("")
          complete(text)
        } else {
          complete(StatusCodes.InternalServerError, "Error")
        }
      case Failure(t: Throwable) =>
        complete(StatusCodes.InternalServerError, t.getMessage)
    }
  }

  private def streamingResponse(response: Source[SolverResponse, NotUsed])
                               (implicit mat: Materializer, ec: ExecutionContext): Route = {
    onComplete(response.runWith(Sink.seq)
      .map(elements =>
        elements.map( r =>
          if (r.response.isReply) {
            r.response.reply.map(_.text).getOrElse("")
          } else if (r.response.isError) {
            r.response.error.map(_.errorMessage).getOrElse("")
          } else {
            ""
          }
        ).mkString(",")
      )) {
      case Success(s: String) => complete(s)
      case Failure(t: Throwable) => complete(StatusCodes.InternalServerError, t.getMessage)
    }
  }

  private def runExampleOnUnaryRPC(log: LoggingAdapter,
                                   client: SolverServiceClient,
                                   request: SolverRequest)
                                  (implicit ec: ExecutionContext): Route = {
    log.info("Analyzing an example on Unary RPC")
    unaryResponse(client.analyzeOnUnaryRPC(request))
  }

  private def runExampleOnServerStreamingRPC(log: LoggingAdapter,
                                             client: SolverServiceClient,
                                             request: SolverRequest)
                                            (implicit mat: Materializer, ec: ExecutionContext): Route = {
    log.info("Analyzing an example on server-streaming RPC")
    streamingResponse(client.analyzeOnServerStreamingRPC(request))
  }

  private def runExampleOnClientStreamingRPC(log: LoggingAdapter,
                                             client: SolverServiceClient,
                                             request: Source[SolverRequest, NotUsed])
                                            (implicit ec: ExecutionContext): Route = {
    log.info("Analyzing an example on client-streaming RPC")
    unaryResponse(client.analyzeOnClientStreamingRPC(request))
  }

  private def runExampleOnBidirectionalRPC(log: LoggingAdapter,
                                           client: SolverServiceClient,
                                           request: Source[SolverRequest, NotUsed])
                                          (implicit mat: Materializer, ec: ExecutionContext): Route = {
    log.info("Analyzing an example on bidirectional RPC")
    streamingResponse(client.analyzeOnBidirectionalStreamingRPC(request))
  }

  def main(args: Array[String]): Unit = {
    implicit val system: ActorSystem = ActorSystem("HttpToGrpc")
    implicit val mat: Materializer = Materializer(system)
    implicit val ec: ExecutionContext = system.dispatcher
    val log: LoggingAdapter = system.log

    val settings = GrpcClientSettings.fromConfig("solver.SolverService")
    val client = SolverServiceClient(settings)

/*    system.scheduler.scheduleAtFixedRate(5.seconds, 5.seconds)(() => {
      log.info("Scheduled analyze christopher")
      val response: Future[SolverResponse] = client.analyzeOnUnaryRPC(SolverRequest("Christopher"))
      response.onComplete { r =>
        log.info("Scheduled say hello response {}", r)
      }
    })*/

    val unaryRPC: Route =
      path("unaryRPC" / Segment) { name =>
        val unaryRequest = SolverRequest(name)
        get {
          log.info("Unary RPC solver request: {}", name)
          runExampleOnUnaryRPC(log, client, unaryRequest)
        }
      }

    val serverStreamingRPC: Route =
      path("serverStreamingRPC" / Segment) { name =>
        val unaryRequest = SolverRequest(name)
        get {
          log.info("Server-streaming RPC solver request: {}", name)
          runExampleOnServerStreamingRPC(log, client, unaryRequest)
        }
      }

    val clientStreamingRPC: Route =
      path("clientStreamingRPC" / Segment) { name =>
        val streamingRequest: Source[SolverRequest, NotUsed] =
          Source(name.codePoints
            .toArray
            .toList
            .map(codePoint => new String(Array[Int](codePoint), 0, 1))
            .map(SolverRequest(_)))
        get {
          log.info("Client-streaming RPC solver request: {}", name)
          runExampleOnClientStreamingRPC(log, client, streamingRequest)
        }
      }

    val bidirectionalStreamingRPC: Route =
      path("bidirectionalStreamingRPC" / Segment) { name =>
        val streamingRequest: Source[SolverRequest, NotUsed] =
          Source(name.codePoints
            .toArray
            .toList
            .map(codePoint => new String(Array[Int](codePoint), 0, 1))
            .map(SolverRequest(_)))
        get {
          log.info("Bidirectional streaming RPC solver request: {}", name)
          runExampleOnBidirectionalRPC(log, client, streamingRequest)
        }
      }

    val unaryRPCBindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", 8080).bindFlow(unaryRPC)

    unaryRPCBindingFuture.onComplete {
      case Success(sb: Http.ServerBinding) =>
        log.info("Unary RPC: Bound: {}", sb)
      case Failure(t: Throwable) =>
        log.error(t, "Unary RPC: Failed to bind. Shutting down")
        system.terminate()
    }

    val serverStreamingRPCBindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", 8081).bindFlow(serverStreamingRPC)

    serverStreamingRPCBindingFuture.onComplete {
      case Success(sb: Http.ServerBinding) =>
        log.info("Server-streaming RPC: Bound: {}", sb)
      case Failure(t: Throwable) =>
        log.error(t, "Server-streaming RPC: Failed to bind. Shutting down")
        system.terminate()
    }

    val clientStreamingRPCBindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", 8082).bindFlow(clientStreamingRPC)

    clientStreamingRPCBindingFuture.onComplete {
      case Success(sb: Http.ServerBinding) =>
        log.info("Client-streaming RPC: Bound: {}", sb)
      case Failure(t: Throwable) =>
        log.error(t, "Client-streaming RPC: Failed to bind. Shutting down")
        system.terminate()
    }

    val bidirectionalStreamingRPCBindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("0.0.0.0", 8083).bindFlow(bidirectionalStreamingRPC)

    bidirectionalStreamingRPCBindingFuture.onComplete {
      case Success(sb: Http.ServerBinding) =>
        log.info("Bidirectional streaming RPC: Bound: {}", sb)
      case Failure(t: Throwable) =>
        log.error(t, "Bidirectional streaming RPC: Failed to bind. Shutting down")
        system.terminate()
    }



  }

}
