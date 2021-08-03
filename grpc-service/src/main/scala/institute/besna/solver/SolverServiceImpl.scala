package institute.besna.solver

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import institute.besna.solver.SolverResponse.Response.Reply
import org.slf4j.Logger

import scala.concurrent.{ExecutionContext, Future}

final class SolverServiceImpl(system: ActorSystem[_]) extends SolverService {
  private implicit val sys: ActorSystem[_] = system
  private implicit val ec: ExecutionContext = system.executionContext
  private val log: Logger = system.log

  private val (inboundHub: Sink[SolverRequest, NotUsed], outboundHub: Source[SolverResponse, NotUsed]) =
    MergeHub.source[SolverRequest]
      .mapAsync(2)(analyze)
      .toMat(BroadcastHub.sink[SolverResponse])(Keep.both)
      .run()

  private def analyze(request: SolverRequest): Future[SolverResponse] = {
    Future.successful(SolverResponse(Reply(SolverReply(s"Hello, ${request.name}"))))
  }

  override def analyzeOnUnaryRPC(request: SolverRequest): Future[SolverResponse] = {
    log.info("analyzeThoughUnaryRPC {}", request)
    analyze(request)
  }

  override def analyzeOnServerStreamingRPC(request: SolverRequest): Source[SolverResponse, NotUsed] = {
    log.info("analyzeThroughServerStreamingRPC {}", request)
    Source(request.name.codePoints().toArray.toList)
      .map(codePoint => SolverResponse(Reply(SolverReply(codePoint.toString))))
  }

  override def analyzeOnClientStreamingRPC(in: Source[SolverRequest, NotUsed]): Future[SolverResponse] = {
    log.info("analyzeThroughClientStreamingRPC")
    in.runWith(Sink.seq)
      .map(elements =>
        SolverResponse(Reply(SolverReply(elements.map(_.name).mkString)))
      )
  }

  override def analyzeOnBidirectionalStreamingRPC(in: Source[SolverRequest, NotUsed]): Source[SolverResponse, NotUsed] = {
    log.info("analyzeThroughBidirectionalStreamingRPC")
    in.runWith(inboundHub)
    outboundHub
  }

}