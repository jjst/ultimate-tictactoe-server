package eu.jjst

import java.util.concurrent.Executors

import cats.effect.concurrent.Ref
import cats.effect.{ Blocker, ContextShift, Sync }
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import eu.jjst.GameServerState.{ AlreadyExists, GameDoesNotExist, SlotTaken }
import eu.jjst.InputMessage.{ JoinGame, LeaveGame, PlayMove }
import eu.jjst.Models._
import eu.jjst.TextCodecs._
import fs2.concurrent.{ Queue, Topic }
import fs2.{ Pipe, Stream }
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.`Content-Type`
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.{ Close, Text }
import org.http4s.{ HttpRoutes, MediaType, Response }

class GameRoutes[F[_]: Sync: ContextShift](
  gameServerState: Ref[F, GameServerState],
  queue: Queue[F, InputMessage],
  topic: Topic[F, OutputMessage]) extends Http4sDsl[F] with LazyLogging {

  private val blocker = {
    val numBlockingThreadsForFilesystem = 4
    val blockingPool = Executors.newFixedThreadPool(numBlockingThreadsForFilesystem)
    Blocker.liftExecutorService(blockingPool)
  }

  val routes: HttpRoutes[F] =
    HttpRoutes.of[F] {
      // Read the current state and format some stats in HTML
      case GET -> Root / "metrics" =>
        val outputStream = Stream
          .eval(gameServerState.get)
          .map { state =>
            val gameCount = state.games.keys.size
            val games = state.games.map { case (gameId, game) => s"$gameId -> $game" }
            s"""
               |<html>
               |<title>Chat Server State</title>
               |<body>
               |<div>Games: ${gameCount}</div>
               |<ul>
               |${games.map("<li>" + _ + "</li>").mkString("\n")}
               |</ul>
               |</body>
               |</html>
              """.stripMargin
          }

        Ok(outputStream, `Content-Type`(MediaType.text.html))

      case PUT -> Root / "games" / gameId => {
        gameServerState
          .modify { state =>
            state.createGame(gameId) match {
              case Right(g) => (g, Created())
              case Left(AlreadyExists) => (state, Conflict())
            }
          }
          .flatten
      }
      case GET -> Root / "games" / gameId / "debug" =>
        val outputStream = Stream
          .eval(gameServerState.get)
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
      case GET -> Root / "games" / gameId / "ws" / p => {
        parsePlayer(p) match {
          case Some(player) => {
            gameServerState
              .modify { state =>
                state.joinGame(gameId, player) match {
                  case Right(g) => (g, createGameWebsocket(gameId, player))
                  // I'm getting 501 back instead and I don't get why, but when running the server it correctly handles this
                  case Left(GameDoesNotExist) => (state, NotFound())
                  case Left(SlotTaken) => (state, Conflict())
                }
              }
              .flatten
          }
          case None =>
            NotFound()
        }
      }
    }

  private def parsePlayer(str: String): Option[Player] = {
    str.toLowerCase match {
      case "x" => Some(Player.X)
      case "o" => Some(Player.O)
      case _ => None
    }
  }

  private def createGameWebsocket(gameId: GameId, player: Player): F[Response[F]] = {
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
              //FIXME: hackish a.f.
              eu.jjst.Text.decode[Move](text.tail.trim) match {
                case Right(move) => Stream(PlayMove(gameId, move))
                case Left(_) => {
                  logger.warn(s"Received invalid input message: $text")
                  Stream.empty
                }
              }
            }

            // Convert the terminal WebSocket event to a User disconnect message
            case Close(_) => Stream(LeaveGame(gameId, player))
          }
          .flatten

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
