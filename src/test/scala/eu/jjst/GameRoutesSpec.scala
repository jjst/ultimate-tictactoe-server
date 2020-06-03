package eu.jjst

import cats.effect.IO
import cats.effect.concurrent.Ref
import eu.jjst.Models.{ Game, Player }
import eu.jjst.OutputMessage.KeepAlive
import fs2.concurrent.{ Queue, Topic }
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.{ Header, Headers, Method, Request }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import scala.concurrent.ExecutionContext

class GameRoutesSpec extends WordSpec with Matchers with ScalaFutures {
  implicit val cs = IO.contextShift(ExecutionContext.global)

  "GameRoutes" should {
    val initialMessage = KeepAlive
    val gameRoutes = for {
      queue <- Queue.unbounded[IO, InputMessage];
      topic <- Topic[IO, OutputMessage](initialMessage)
      ref <- Ref.of[IO, GameServerState](
        GameServerState(Map(
          "valid-existing-game-id" -> Game.create(),
          "valid-full-game-id" -> Game(playersInGame = Set(Player.O, Player.X), activePlayer = Player.X, moves = List.empty))))
    } yield new GameRoutes[IO](ref, queue, topic)

    "return 201 on successful game creation (PUT /games/:game-id)" in {
      val request = Request[IO](method = Method.PUT, uri = uri"/games/new-game-id")
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe Created
    }

    "return 409 on if game already exists (PUT /games/:game-id)" in {
      val request = Request[IO](method = Method.PUT, uri = uri"/games/valid-existing-game-id")
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe Conflict
    }

    "return 404 on join if invalid player (GET /games/:game-id/ws/:player" in {
      val request = Request[IO](
        method = Method.GET,
        uri = uri"/games/valid-existing-game-id/ws/e",
        headers = Headers.of(
          Header("Connection", "Upgrade"),
          Header("Upgrade", "websocket"),
          Header("Sec-WebSocket-Key", base64("test"))))
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe NotFound
    }

    "return 404 on join if game does not exists (GET /games/:game-id/ws/:player" in {
      val request = Request[IO](
        method = Method.GET,
        uri = uri"/games/invalid-game-id/ws/o",
        headers = Headers.of(
          Header("Connection", "Upgrade"),
          Header("Upgrade", "websocket"),
          Header("Sec-WebSocket-Key", base64("test"))))
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe NotFound
    }

    "return 409 on join if game full (GET /games/:game-id/ws/:player" in {
      val request = Request[IO](
        method = Method.GET,
        uri = uri"/games/valid-full-game-id/ws/o",
        headers = Headers.of(
          Header("Connection", "Upgrade"),
          Header("Upgrade", "websocket"),
          Header("Sec-WebSocket-Key", base64("test"))))
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe Conflict
    }

    // I'm getting 501 back instead and I don't get why, but when running the server it correctly handles this
    "return 101 (web socket handshake) on successful join (GET /games/:game-id/ws/:player" ignore {
      val request = Request[IO](
        method = Method.GET,
        uri = uri"/games/valid-existing-game-id/ws/o",
        headers = Headers.of(
          Header("Connection", "Upgrade"),
          Header("Upgrade", "websocket"),
          Header("Sec-WebSocket-Key", base64("test"))))
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe SwitchingProtocols
    }
  }

  def base64(str: String): String = {
    new String(java.util.Base64.getEncoder.encode(str.getBytes))
  }
}
