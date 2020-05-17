package eu.jjst

import cats.effect.IO
import cats.effect.concurrent.Ref
import eu.jjst.Models.OutputMessage.KeepAlive
import eu.jjst.Models.{ Game, GameServerState, InputMessage, OutputMessage }
import fs2.concurrent.{ Queue, Topic }
import org.http4s.{ Method, Request }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }
import org.http4s.dsl.io._
import org.http4s.implicits._

import scala.concurrent.ExecutionContext

class GameRoutesSpec extends WordSpec with Matchers with ScalaFutures {
  implicit val cs = IO.contextShift(ExecutionContext.global)

  "GameRoutes" should {
    val initialMessage = KeepAlive
    val gameRoutes = for {
      queue <- Queue.unbounded[IO, InputMessage];
      topic <- Topic[IO, OutputMessage](initialMessage)
      ref <- Ref.of[IO, GameServerState](GameServerState(Map("some-test-game" -> Game.create())))
    } yield new GameRoutes[IO](ref, queue, topic)

    "return 201 on successful game creation (PUT /games/:game-id)" in {
      val request = Request[IO](method = Method.PUT, uri = uri"/games/new-game-id")
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe Created
    }

    "return 409 on if game already exists (PUT /games/:game-id)" in {
      val request = Request[IO](method = Method.PUT, uri = uri"/games/some-test-game")
      val response = gameRoutes.flatMap { gr =>
        gr.routes.orNotFound.run(request)
      }.unsafeRunSync()
      response.status shouldBe Conflict
    }
  }
}
