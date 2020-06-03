package eu.jjst

import eu.jjst.InputMessage.JoinGame
import eu.jjst.Models.{ Coords, Game, Move, Player }
import eu.jjst.OutputMessage.{ MovePlayed, PlayerJoined }
import org.scalatest.{ Matchers, WordSpec }

class GameServerStateSpec extends WordSpec with Matchers {

  "GameServerState update" should {
    "send all current game moves from first to last to joining player" in {
      val existingMoves = List(
        Move(Player.O, Coords(1, 2), Coords(2, 1)),
        Move(Player.X, Coords(1, 1), Coords(2, 1)))
      val gs = GameServerState(Map("1" -> Game(playersInGame = Set.empty, activePlayer = Player.X, moves = existingMoves)))
      val (newGameState, msgs) = gs.update(JoinGame("1", Player.O))
      newGameState shouldBe gs
      msgs shouldBe List(
        PlayerJoined(Player.O),
        MovePlayed(Move(Player.X, Coords(1, 1), Coords(2, 1)), Some(Player.O)),
        MovePlayed(Move(Player.O, Coords(1, 2), Coords(2, 1)), Some(Player.O)))
    }
  }

}
