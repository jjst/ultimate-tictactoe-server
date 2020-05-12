package eu.jjst

object Models {
  case class Coords(x: Int, y: Int)
  case class Move(player: Player, outerBoardCoords: Coords, innerBoardCoords: Coords)

  //FIXME: use tagged types or something typesafer
  type GameId = String
  type PlayerId = String

  sealed trait Player

  object Player {
    case object X extends Player
    case object O extends Player
  }

  case class GameState(
    activePlayer: Player,
    moves: List[Move])

  case class AllGames(games: Map[GameId, GameState])

  sealed trait InputMessage {
    val playerId: PlayerId
  }

  object InputMessage {
    case class JoinGame(playerId: PlayerId, gameId: GameId) extends InputMessage
    case class LeaveGame(playerId: PlayerId, gameId: GameId) extends InputMessage
    case class PlayMove(playerId: PlayerId, gameId: GameId, move: Move) extends InputMessage
  }

  trait OutputMessage {
    // To support stream filtering
    def forPlayer(targetPlayer: PlayerId): Boolean
    def toString: String
  }

  object OutputMessage {
    case class GameChange(move: Move) extends OutputMessage {
      override def forPlayer(targetPlayer: PlayerId): Boolean = true //FIXME
    }
  }
}
