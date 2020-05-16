package eu.jjst

import com.typesafe.scalalogging.LazyLogging
import eu.jjst.Models.InputMessage.{ JoinGame, LeaveGame, PlayMove }
import eu.jjst.Models.OutputMessage.{ GameChange, GameStarted, WaitingForPlayers }

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

  object Game {
    def create(): Game = {
      Game(
        playersInGame = Set.empty,
        activePlayer = Player.X,
        moves = List.empty)
    }
  }

  case class Game(
    playersInGame: Set[Player],
    activePlayer: Player,
    moves: List[Move]) {
    def add(p: Player): Game = {
      this.copy(playersInGame + p)
    }
    def enoughPlayers: Boolean = playersInGame.size >= 2
  }

  case class GameServerState(games: Map[GameId, Game]) extends LazyLogging {
    def update(inputMessage: InputMessage): (GameServerState, Seq[OutputMessage]) = inputMessage match {
      case JoinGame(gameId, player) => {
        joinGame(gameId, player)
      }
      case LeaveGame(gameId, player) => {
        leaveGame(gameId, player)
      }
      case PlayMove(gameId, move) => {
        games.get(gameId) match {
          case None => {
            logger.error(s"[game: $gameId] Game doesn't exist, cannot play move")
            (this, Seq.empty)
          }
          case Some(g) if !g.enoughPlayers => {
            logger.error(s"[game: $gameId] Game not in progress")
            (this, Seq.empty)
          }
          case Some(Game(players, activePlayer, moves)) => {
            if (move.player != activePlayer) {
              logger.error(s"[game: $gameId] ${move.player} cannot play, they are not the active player!")
              (this, Seq.empty)
            } else {
              val newGameState = {
                val newActivePlayer = activePlayer match {
                  case Player.X => Player.O
                  case Player.O => Player.X
                }
                Game(players, newActivePlayer, move :: moves)
              }
              logger.debug(s"[game: $gameId] ${move.player} played move: ${move}")
              (this.copy(games.updated(gameId, newGameState)), Seq(GameChange(move)))
            }
          }
        }
      }
    }

    private def joinGame(gameId: GameId, player: Player): (GameServerState, Seq[OutputMessage]) = {
      games.get(gameId) match {
        case None => {
          logger.info(s"[game: $gameId] Game doesn't exist, creating")
          createGame(gameId).joinGame(gameId, player)
        }
        case Some(game) => {
          joinExistingGame(gameId, game, player)
        }
      }
    }

    private def leaveGame(gameId: GameId, player: Player): (GameServerState, Seq[OutputMessage]) = {
      games.get(gameId) match {
        case None => {
          logger.warn(s"[game: $gameId] Game doesn't exist, creating")
          (this, Seq.empty)
        }
        case Some(game) => {
          (this.copy(games.updated(gameId, game.copy(playersInGame = game.playersInGame - player))), Seq(WaitingForPlayers))
        }
      }
    }

    private def createGame(gameId: GameId): GameServerState = {
      GameServerState(this.games.updated(gameId, Game.create()))
    }

    private def joinExistingGame(gameId: GameId, game: Game, player: Player) = {
      val newGame = game.add(player)
      logger.info(s"[game: $gameId] Player $player joined")
      logger.debug(s"[game: $gameId] New game state: $newGame")
      val msgs = if (newGame.enoughPlayers) {
        logger.info(s"[game: ${gameId}] Enough players joined, starting game")
        Seq(GameStarted)
      } else Seq.empty
      val gameServerState = GameServerState(this.games.updated(gameId, newGame))
      (gameServerState, msgs)
    }
  }

  sealed trait InputMessage {
    val gameId: GameId
  }

  object InputMessage {
    case class JoinGame(gameId: GameId, player: Player) extends InputMessage
    case class LeaveGame(gameId: GameId, player: Player) extends InputMessage
    case class PlayMove(gameId: GameId, move: Move) extends InputMessage
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
    case object WaitingForPlayers extends OutputMessage {
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
