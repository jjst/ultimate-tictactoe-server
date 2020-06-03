package eu.jjst

import com.typesafe.scalalogging.LazyLogging
import eu.jjst.GameServerState._
import eu.jjst.InputMessage.{ JoinGame, LeaveGame, PlayMove }
import eu.jjst.Models._
import eu.jjst.OutputMessage.{ MovePlayed, GameStarted, PlayerJoined, PlayerLeft }

object Models {
  case class Coords(x: Int, y: Int)
  case class Move(player: Player, outerBoardCoords: Coords, innerBoardCoords: Coords)

  //FIXME: use tagged types or something typesafer
  type GameId = String

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
          logger.error(s"[game: $gameId] Game doesn't exist, cannot join")
          (this, Seq.empty)
        case Some(game) => {
          if (game.enoughPlayers)
            logger.debug(s"[game: $gameId] Enough players to start game, sending game started event")
          else
            logger.debug(s"[game: $gameId] A player joined, but I'm still missing a player to start")
          // We send:
          // - a player joined event
          // + all the moves played so far
          val outputMessages: List[OutputMessage] =
            PlayerJoined(player) :: game.moves.map(sendAlreadyPlayedMove(player)) ::: (if (game.enoughPlayers) List(GameStarted) else List.empty)
          (this, outputMessages)
        }
      }
    }
    case LeaveGame(gameId, player) => {
      games.get(gameId) match {
        case None => {
          (this, Seq.empty)
        }
        case Some(game) => {
          (this.copy(games.updated(gameId, game.copy(playersInGame = game.playersInGame - player))), Seq(PlayerLeft(player)))
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
            (this.copy(games.updated(gameId, newGameState)), Seq(MovePlayed(move)))
          }
        }
      }
    }
  }

  def sendAlreadyPlayedMove(targetPlayer: Player)(move: Move) = MovePlayed(move, Some(targetPlayer))

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
