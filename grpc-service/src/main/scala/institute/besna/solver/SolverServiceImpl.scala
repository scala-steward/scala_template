package institute.besna.solver

import akka.NotUsed
import akka.event.LoggingAdapter
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Keep, MergeHub, Sink, Source}
import institute.besna.solver.SolverResponse.Response.Reply

import scala.concurrent.Future

final class SolverServiceImpl(materializer: Materializer, log: LoggingAdapter) extends SolverService {
  private implicit val mat: Materializer = materializer
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
    import materializer.executionContext
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