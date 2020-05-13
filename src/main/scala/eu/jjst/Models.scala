package eu.jjst

import com.typesafe.scalalogging.LazyLogging
import eu.jjst.Models.InputMessage.{JoinGame, PlayMove}
import eu.jjst.Models.OutputMessage.GameStarted

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

  trait GameState

  case class WaitingForPlayers(players: List[PlayerId]) extends GameState {
    def readyToStart: Boolean = players.size >= 2
    def add(p: PlayerId): GameState = {
      WaitingForPlayers(p :: players)
    }
    def start(): GameInProgress = GameInProgress(activePlayer = Player.X, moves = List.empty)
  }

  case class GameInProgress(
    activePlayer: Player,
    moves: List[Move]) extends GameState

  case class GameServerState(games: Map[GameId, GameState]) extends LazyLogging {
    def update(inputMessage: InputMessage): (GameServerState, Seq[OutputMessage]) = inputMessage match {
      case JoinGame(playerId, gameId) => {
        joinGame(playerId, gameId)
      }
      case PlayMove(playerId, gameId, move) => {

      }
    }

    private def joinGame(playerId: PlayerId, gameId: GameId): (GameServerState, Seq[OutputMessage]) = {
      games.get(gameId) match {
        case None => {
          logger.error(s"Unknown game id: $gameId")
          (this, Seq.empty)
        }
        case Some(game) => {
          game match {
            case game: WaitingForPlayers => {
              val newGame = {
                game.add(playerId)
                if (game.readyToStart) {
                  game.start()
                } else {
                  game
                }
              }
              val gameServerState = GameServerState(games.updated(gameId, newGame))
              val msgs = newGame match {
                case _: GameInProgress => Seq(GameStarted)
                case _ => Seq.empty
              }
              (gameServerState, msgs)
            }
          }
        }
      }
    }
  }

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
    case object GameStarted extends OutputMessage {
      override def forPlayer(targetPlayer: PlayerId): Boolean = true //FIXME
    }
    case class GameChange(move: Move) extends OutputMessage {
      override def forPlayer(targetPlayer: PlayerId): Boolean = true //FIXME
    }
    case object KeepAlive extends OutputMessage {
      override def forPlayer(targetPlayer: PlayerId): Boolean = true
    }
  }
}
