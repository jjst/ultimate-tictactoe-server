package eu.jjst

import eu.jjst.models.Move

sealed trait GameMessage

object GameMessage {
  case object PlayerJoined extends GameMessage
  case object PlayerLeft extends GameMessage
  case class PlayerMoved(move: Move) extends GameMessage
}
