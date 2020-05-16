package eu.jjst

import java.io.File
import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import cats.effect.{ Blocker, ContextShift, Sync }
import eu.jjst.Models.InputMessage.{ JoinGame, LeaveGame, PlayMove }
import eu.jjst.Models.{ Game, GameServerState, InputMessage, Move, OutputMessage, Player }
import fs2.concurrent.{ Queue, Topic }
import fs2.{ Pipe, Stream }
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{ Close, Text }
import org.http4s.{ HttpRoutes, MediaType, StaticFile }
import TextCodecs._
import com.typesafe.scalalogging.LazyLogging

class GameRoutes[F[_]: Sync: ContextShift](
  games: Ref[F, GameServerState],
  queue: Queue[F, InputMessage],
  topic: Topic[F, OutputMessage]) extends Http4sDsl[F] with LazyLogging {

  private val blocker = {
    val numBlockingThreadsForFilesystem = 4
    val blockingPool = Executors.newFixedThreadPool(numBlockingThreadsForFilesystem)
    Blocker.liftExecutorService(blockingPool)
  }

  val routes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      // Static resources
      case request @ GET -> Root =>
        StaticFile
          .fromFile(new File("static/index.html"), blocker, Some(request))
          .getOrElseF(NotFound())

      case request @ GET -> Root / "chat.js" =>
        StaticFile
          .fromFile(new File("static/chat.js"), blocker, Some(request))
          .getOrElseF(NotFound())

      // Read the current state and format some stats in HTML
      case GET -> Root / "metrics" =>
        val outputStream = Stream
          .eval(games.get)
          .map(state =>
            s"""
               |<html>
               |<title>Chat Server State</title>
               |<body>
               |<div>Games: ${state.games.keys.size}</div>
               |</body>
               |</html>
              """.stripMargin)

        Ok(outputStream, `Content-Type`(MediaType.text.html))

      case POST -> Root / "games" =>
        Ok(???, `Content-Type`(MediaType.text.html))

      case GET -> Root / "games" / gameId / "debug" =>
        val outputStream = Stream
          .eval(games.get)
          .map(state =>
            s"""
               |<html>
               |<title>Game $gameId:</title>
               |<body>
               |<div>${state.games(gameId).toString}</div>
               |</body>
               |</html>
              """.stripMargin)

        Ok(outputStream, `Content-Type`(MediaType.text.html))
      // Bind a WebSocket connection for a user
      case GET -> Root / "games" / "ws" / gameId / p => {
        val player: Player = p.toLowerCase match {
          case "x" => Player.X
          case "o" => Player.O
        }
        // Routes messages from our "topic" to a WebSocket
        val toClient =
          topic
            .subscribe(1000)
            .map(msg => Text(eu.jjst.Text.encode(msg)))

        // Function that converts a stream of one type to another. Effectively an external "map" function
        def processInput(wsfStream: Stream[F, WebSocketFrame]) = {
          // Stream of initialization events for a user
          val entryStream = Stream.emits(Seq(JoinGame(gameId, player)))

          // Stream that transforms between raw text from the client and parsed InputMessage objects
          val parsedWebSocketInput =
            wsfStream
              .collect {
                case Text(text, _) => {
                  PlayMove(gameId, eu.jjst.Text.decode[Move](text))
                }

                // Convert the terminal WebSocket event to a User disconnect message
                case Close(_) => LeaveGame(gameId, player)
              }

          // Create a stream that has all of the user input sandwiched between the entry and disconnect messages
          (entryStream ++ parsedWebSocketInput).through(queue.enqueue)
        }

        // WebSocketBuilder needs a "pipe" which is a type alias for a stream transformation function like processInput above
        // This variable is not necessary to compile, but is included to clarify the exact type of Pipe.
        val inputPipe: Pipe[F, WebSocketFrame, Unit] = processInput

        // Build the WebSocket handler
        logger.debug("Creating new web socket handler")
        WebSocketBuilder[F].build(toClient, inputPipe)
      }
    }
}
