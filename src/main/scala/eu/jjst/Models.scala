package eu.jjst

import com.typesafe.scalalogging.LazyLogging
import eu.jjst.GameServerState._
import eu.jjst.Models.InputMessage.{JoinGame, LeaveGame, PlayMove}
import eu.jjst.Models.OutputMessage.{GameChange, GameStarted, PlayerJoined, PlayerLeft}
import eu.jjst.Models._

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
    case object PlayerJoined extends OutputMessage {
      override def forPlayer(targetPlayer: PlayerId): Boolean = true //FIXME
    }
    case object PlayerLeft extends OutputMessage {
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

object GameServerState {
  sealed trait CreateError
  case object AlreadyExists extends CreateError

  sealed trait JoinError
  case object GameDoesNotExist extends JoinError
  case object SlotTaken extends JoinError

  sealed trait LeaveError
  type GameUpdate[E] = Either[E, GameServerState]
}

case class GameServerState(games: Map[GameId, Game]) extends LazyLogging {
  def update(inputMessage: InputMessage): (GameServerState, Seq[OutputMessage]) = inputMessage match {
    //FIXME: this is weird - we receive a JoinGame event but don't do anything with it
    case JoinGame(gameId, player) => {
      games.get(gameId) match {
        case None =>
          logger.error(s"[game: $gameId] Game doesn't exist, cannot play move")
          (this, Seq(PlayerJoined))
        case Some(game) if game.enoughPlayers => {
          logger.debug(s"[game: $gameId] Enough players to start game, sending game started event")
          (this, Seq(PlayerJoined, GameStarted))
        }
        case _ => {
          logger.debug(s"[game: $gameId] A player joined, but I'm still missing a player to start")
          (this, Seq.empty)
        }
      }
    }
    case LeaveGame(gameId, player) => {
      games.get(gameId) match {
        case None => {
          (this, Seq.empty)
        }
        case Some(game) => {
          (this.copy(games.updated(gameId, game.copy(playersInGame = game.playersInGame - player))), Seq(PlayerLeft))
        }
      }
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

  def joinGame(gameId: GameId, player: Player): GameUpdate[JoinError] = {
    games.get(gameId) match {
      case None => {
        logger.debug(s"[game: $gameId] Does not exist, player $player cannot join")
        Left(GameDoesNotExist)
      }
      case Some(game) if game.playersInGame.contains(player) => {
        Left(SlotTaken)
      }
      case Some(game) => {
        Right(joinExistingGame(gameId, game, player))
      }
    }
  }

  def createGame(gameId: GameId): GameUpdate[CreateError] = {
    this.games.get(gameId) match {
      case Some(_) => Left(AlreadyExists)
      case None => {
        val newState = GameServerState(this.games.updated(gameId, Game.create()))
        Right(newState)
      }
    }
  }

  private def joinExistingGame(gameId: GameId, game: Game, player: Player) = {
    val newGame = game.add(player)
    logger.info(s"[game: $gameId] Player $player joined")
    logger.debug(s"[game: $gameId] New game state: $newGame")
    GameServerState(this.games.updated(gameId, newGame))
  }
}
