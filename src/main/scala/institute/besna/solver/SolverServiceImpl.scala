package institute.besna.solver

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import institute.besna.solver.SolverResponse.Response.Reply
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future}

final class SolverServiceImpl(system: ActorSystem[_]) extends SolverService {
  private implicit val sys: ActorSystem[_]   = system
  private implicit val ec:  ExecutionContext = system.executionContext
  private val log:          Logger           = system.log

  private val (inboundHub: Sink[SolverRequest, NotUsed], outboundHub: Source[SolverResponse, NotUsed]) = {
    MergeHub
      .source[SolverRequest]
      .mapAsync(2)(analyze)
      .toMat(BroadcastHub.sink[SolverResponse])(Keep.both)
      .run()
    /*MergeHub.source[SolverRequest]
      .map(analyze$)
      .toMat(BroadcastHub.sink[SolverResponse])(Keep.both)
      .run()*/
  }

  private val apiName    = "APINAME"
  private val apiVersion = "1.0.0"

  private def analyze$(request: SolverRequest): SolverResponse = {
    SolverResponse(Reply(SolverReply(apiName, apiVersion, s"Hello, ${request.name}")))
  }

  private def analyze(request: SolverRequest): Future[SolverResponse] = {
    Future.successful(analyze$(request))
  }

  override def analyzeOnUnaryRPC(request: SolverRequest): Future[SolverResponse] = {
    log.info("analyzeOnUnaryRPC {}", request)
    analyze(request)
  }

  override def analyzeOnServerStreamingRPC(request: SolverRequest): Source[SolverResponse, NotUsed] = {
    log.info("analyzeOnServerStreamingRPC {}", request)
    Source(request.name.codePoints().toArray.toList)
      .map(codePoint =>
        SolverResponse(Reply(SolverReply(apiName, apiVersion, new String(Array[Int](codePoint), 0, 1))))
      )
  }

  override def analyzeOnClientStreamingRPC(in: Source[SolverRequest, NotUsed]): Future[SolverResponse] = {
    log.info("analyzeOnClientStreamingRPC")
    in.runWith(Sink.seq)
      .map(elements =>
        SolverResponse(Reply(SolverReply(apiName, apiVersion, elements.map(_.name.concat("+")).mkString)))
      )
  }

  @SuppressWarnings(Array[String]("org.wartremover.warts.NonUnitStatements"))
  override def analyzeOnBidirectionalStreamingRPC(
      in: Source[SolverRequest, NotUsed]
  ): Source[SolverResponse, NotUsed] = {
    log.info("analyzeOnBidirectionalStreamingRPC")
    in.runWith(inboundHub)
    outboundHub
  }
}
