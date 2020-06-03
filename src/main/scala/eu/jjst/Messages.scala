package eu.jjst

import eu.jjst.Models.{ GameId, Move, Player }

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
  def forPlayer(p: Player): Boolean
}

object OutputMessage {
  case object GameStarted extends OutputMessage {
    override def forPlayer(p: Player): Boolean = true //FIXME
  }
  case class PlayerJoined(player: Player) extends OutputMessage {
    override def forPlayer(p: Player): Boolean = true //FIXME
  }
  case class PlayerLeft(player: Player) extends OutputMessage {
    override def forPlayer(p: Player): Boolean = true //FIXME
  }
  case class MovePlayed(move: Move, `for`: Option[Player] = None) extends OutputMessage {
    override def forPlayer(p: Player): Boolean = `for` match {
      case None => true
      case Some(target) => p == target
    }
  }
  case object KeepAlive extends OutputMessage {
    override def forPlayer(p: Player): Boolean = true //FIXME
  }
}
