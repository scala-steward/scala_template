package institute.besna.solver

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer

import scala.concurrent.{ExecutionContext, Future}

object SolverServer {
  def main(args: Array[String]): Unit = {
    val system: ActorSystem = ActorSystem("GreeterServer")
    new SolverServer(system).run()
  }
}

final class SolverServer(system: ActorSystem) {
  def run(): Future[Http.ServerBinding] = {
    implicit val sys: ActorSystem = system
    implicit val mat: Materializer = Materializer(sys)
    implicit val ec: ExecutionContext = sys.dispatcher

    val service: HttpRequest => Future[HttpResponse] =
      SolverServiceHandler(new SolverServiceImpl(mat, system.log))

    val bound: Future[Http.ServerBinding] = Http().newServerAt("0.0.0.0", 8080).bind(service)

    bound.foreach { binding =>
      sys.log.info("gRPC server bound to: {}", binding.localAddress)
    }

    bound
  }
}
