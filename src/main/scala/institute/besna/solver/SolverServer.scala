package institute.besna.solver

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pki.pem.{DERPrivateKeyLoader, PEMDecoder}
import com.typesafe.config.ConfigFactory
import org.slf4j.Logger

import java.net.InetSocketAddress
import java.security.{KeyStore, SecureRandom}
import java.security.cert.{Certificate, CertificateFactory}
import javax.net.ssl.{KeyManagerFactory, SSLContext}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.Source
import scala.util.{Failure, Success}

object SolverServer {

  def main(args: Array[String]): Unit = {
    val conf = ConfigFactory.parseString("akka.http.server.preview.enable-http2 = on")
      .withFallback(ConfigFactory.defaultApplication())
    val system = ActorSystem[Nothing](Behaviors.empty, "SolverServer", conf)
    new SolverServer(system).run()
  }

}

final class SolverServer(system: ActorSystem[_]) {

  def run(): Future[Http.ServerBinding] = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext
    val log: Logger = sys.log

    val service: HttpRequest => Future[HttpResponse] = SolverServiceHandler(new SolverServiceImpl(system))

    val bound: Future[Http.ServerBinding] = Http(system)
      .newServerAt(interface = "127.0.0.1", port = 8080)
      .enableHttps(serverHttpContext)
      .bind(service)
      .map(_.addToCoordinatedShutdown(hardTerminationDeadline = 10.seconds))

    bound.onComplete {
      case Success(binding: Http.ServerBinding) =>
        val address: InetSocketAddress = binding.localAddress
        log.info("gRPC server bound to {}:{}", address.getHostString, address.getPort)
      case Failure(t: Throwable) =>
        log.info("Failed to bind gRPC endpoint, terminating system", t)
        system.terminate()
    }

    bound
  }

  private def serverHttpContext: HttpsConnectionContext = {
    val privateKey = DERPrivateKeyLoader.load(PEMDecoder.decode(readPrivateKeyPem()))
    val fact = CertificateFactory.getInstance("X.509")
    val cer = fact.generateCertificate(classOf[SolverServer].getResourceAsStream("/certs/server1.pem"))
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null)
    ks.setKeyEntry("private", privateKey, new Array[Char](0), Array[Certificate](cer))
    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, null)
    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom())
    ConnectionContext.httpsServer(context)
  }

  private def readPrivateKeyPem(): String = Source.fromResource("certs/server1.key").mkString

}