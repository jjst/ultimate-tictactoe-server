package eu.jjst

import com.typesafe.scalalogging.LazyLogging
import eu.jjst.Models.InputMessage.{ JoinGame, LeaveGame, PlayMove }
import eu.jjst.Models.OutputMessage.{ GameChange, GameStarted }

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

  trait Game {
    def enoughPlayers: Boolean
  }

  object Game {
    def create(): Game = {
      WaitingForPlayers(Set(Player.X, Player.O))
    }
  }

  case class WaitingForPlayers(players: Set[Player]) extends Game {
    override def enoughPlayers: Boolean = players.isEmpty
    def add(p: Player): Game = {
      val waitingOnPlayers = players - p
      if (waitingOnPlayers.isEmpty) this.start()
      else WaitingForPlayers(waitingOnPlayers)
    }
    private def start(): GameInProgress = GameInProgress(activePlayer = Player.X, moves = List.empty)
  }

  case class GameInProgress(
    activePlayer: Player,
    moves: List[Move]) extends Game {
    override def enoughPlayers: Boolean = true
  }

  case class GameServerState(games: Map[GameId, Game]) extends LazyLogging {
    def update(inputMessage: InputMessage): (GameServerState, Seq[OutputMessage]) = inputMessage match {
      case JoinGame(gameId, player) => {
        joinGame(gameId, player)
      }
      case LeaveGame(gameId, player) => {
        ???
      }
      case PlayMove(gameId, move) => {
        games.get(gameId) match {
          case None => {
            logger.error(s"[game: $gameId] Game doesn't exist, cannot play move")
            (this, Seq.empty)
          }
          case Some(_: WaitingForPlayers) => {
            logger.error(s"[game: $gameId] Game not in progress")
            (this, Seq.empty)
          }
          case Some(GameInProgress(activePlayer, moves)) => {
            if (move.player != activePlayer) {
              logger.error(s"[game: $gameId] ${move.player} cannot play, they are not the active player!")
              (this, Seq.empty)
            } else {
              val newGameState = {
                val newActivePlayer = activePlayer match {
                  case Player.X => Player.O
                  case Player.O => Player.X
                }
                GameInProgress(newActivePlayer, move :: moves)
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

    private def createGame(gameId: GameId): GameServerState = {
      GameServerState(this.games.updated(gameId, Game.create()))
    }

    private def joinExistingGame(gameId: GameId, game: Game, player: Player) = {
      game match {
        case game: WaitingForPlayers => {
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
    case class GameChange(move: Move) extends OutputMessage {
      override def forPlayer(targetPlayer: PlayerId): Boolean = true //FIXME
    }
    case object KeepAlive extends OutputMessage {
      override def forPlayer(targetPlayer: PlayerId): Boolean = true
    }
  }
}
