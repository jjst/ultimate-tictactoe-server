package eu.jjst

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.model.ws.{ BinaryMessage, Message, TextMessage }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.PathDirectives.path
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.Timeout

import scala.concurrent.duration._

import io.circe.parser.decode

import io.circe.syntax._

trait WebSocketRoutes extends JsonSupport {

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem
  implicit def materializer: ActorMaterializer

  lazy val game = Game.create(system)

  lazy val log = Logging(system, classOf[WebSocketRoutes])

  implicit lazy val timeout = Timeout(5.seconds) // usually we'd obtain the timeout from the system's configuration

  def websocketGameFlow(ip: RemoteAddress): Flow[Message, Message, Any] = {
    Flow[Message].mapConcat {
      case TextMessage.Strict(msg) => {
        decode[GameMessage](msg) match {
          case Right(msg) => List(msg)
          case Left(error) =>
            log.error(s"Got invalid message: $error")
            Nil
        }
      }
      case tm: TextMessage =>
        tm.textStream.runWith(Sink.ignore)
        Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }
      .via(game.gameFlow(Player(ip)))
      .map(gameMsg => TextMessage.Strict(gameMsg.asJson.noSpaces))
  }

  val websocketRoute =
    path("game") {
      extractClientIP { ip =>
        handleWebSocketMessages(websocketGameFlow(ip))
      }
    }
}
