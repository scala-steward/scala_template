package institute.besna.solver

import akka.{Done, NotUsed}
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import org.slf4j.Logger

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

@SuppressWarnings(
  Array[String](
    "org.wartremover.warts.Any",
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.NonUnitStatements"
  )
)
final class SolverServiceImplSpec extends AnyWordSpec with BeforeAndAfterAll with should.Matchers with ScalaFutures {

  private val testKit:           ActorTestKit     = ActorTestKit()
  private implicit val patience: PatienceConfig   = PatienceConfig(scaled(5.seconds), scaled(100.millis))
  private implicit val system:   ActorSystem[_]   = testKit.system
  private val log:               Logger           = system.log
  private implicit val ec:       ExecutionContext = system.executionContext
  private val service = new SolverServiceImpl(system)
  override def afterAll(): Unit = testKit.shutdownTestKit()

  private def responseToString(r: SolverResponse): String = {
    if (r.response.isReply) {
      r.response.reply.map(_.text).getOrElse("")
    } else if (r.response.isError) {
      r.response.error.map(_.errorMessage).getOrElse("")
    } else {
      "ERROR"
    }
  }

  private def unaryResponse(response: Future[SolverResponse]): Future[String] =
    response
      .map(responseToString)
      .recover { case t: Throwable => log.error(t.getMessage); "" }

  private def streamingResponse(
      response: Source[SolverResponse, NotUsed]
  ): Future[String] = {
    response
      .runWith(Sink.seq[SolverResponse])
      .map(_.map(responseToString).mkString("-"))
      .recover { case t: Throwable => log.error(t.getMessage); "" }
  }

  private val apiName      = "APINAME"
  private val name         = "Bob"
  private val unaryRequest = SolverRequest(apiName = apiName, name = name)
  private val streamingRequest: Source[SolverRequest, NotUsed] =
    Source(
      name.codePoints.toArray.toList
        .map(codePoint => new String(Array[Int](codePoint), 0, 1))
        .map(codePointStr => SolverRequest(apiName = apiName, name = codePointStr))
    )

  "SolverServiceImpl" should {
    "reply to single request on unary RPC" in {
      val response: Future[SolverResponse] = service.analyzeOnUnaryRPC(unaryRequest)
      unaryResponse(response).futureValue should ===("Hello, Bob")
    }

    "reply to single request on server-streaming RPC" in {
      val response: Source[SolverResponse, NotUsed] = service.analyzeOnServerStreamingRPC(unaryRequest)
      streamingResponse(response).futureValue should ===("B-o-b")
    }

    "reply to single request on client-streaming RPC" in {
      val response: Future[SolverResponse] = service.analyzeOnClientStreamingRPC(streamingRequest)
      unaryResponse(response).futureValue should ===("B+o+b+")
    }

    "reply to single request on bidirectional streaming RPC" in {
      //TODO: https://github.com/akka/akka/issues/27163
      val response: Source[SolverResponse, NotUsed] = service.analyzeOnBidirectionalStreamingRPC(streamingRequest)
      val correctResponses = mutable.Queue[String]("Hello, B", "Hello, o", "Hello, b")
      val done: Future[Done] = response.runForeach { r =>
        val element: String = correctResponses.dequeue()
        log.info(r.response.reply.map(_.text).getOrElse(""))
        log.info(element)
        import cats.implicits._
        if (r.response.reply.map(_.text).getOrElse("") =!= element) {
          log.info("FAIL: {} {}", r.response.reply.map(_.text).getOrElse(""), element)
          throw new Exception("FAILURE")
        }
      }
      done.onComplete {
        case Success(_) =>
        case Failure(t: Throwable) =>
          log.error(t.getMessage)
      }
    }
  }

}
