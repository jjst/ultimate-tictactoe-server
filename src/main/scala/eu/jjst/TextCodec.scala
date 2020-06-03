package eu.jjst

import com.typesafe.scalalogging.LazyLogging
import eu.jjst.Models.{ Coords, Move, Player }
import eu.jjst.OutputMessage.{ GameStarted, KeepAlive, MovePlayed, PlayerJoined, PlayerLeft }

trait TextEncoder[T] {
  def encode(t: T): String
}

trait TextDecodeError
object TextDecodeError extends TextDecodeError

trait TextDecoder[T] {
  def decode(txt: String): Either[TextDecodeError, T]
}

object Text {
  def decode[T](txt: String)(implicit textDecoder: TextDecoder[T]): Either[TextDecodeError, T] = textDecoder.decode(txt)
  def encode[T](t: T)(implicit textEncoder: TextEncoder[T]): String = textEncoder.encode(t)
}

object TextCodecs extends LazyLogging {
  implicit val playerEncoder: TextEncoder[Player] = new TextEncoder[Player] {
    override def encode(p: Player): String = p match {
      case Player.X => "X"
      case Player.O => "O"
    }
  }

  implicit val moveEncoder: TextEncoder[Move] = new TextEncoder[Move] {
    override def encode(move: Move): String = {
      s"${Text.encode(move.player)} ${move.outerBoardCoords.x} ${move.outerBoardCoords.y} ${move.innerBoardCoords.x} ${move.innerBoardCoords.y}"
    }
  }

  implicit val moveDecoder: TextDecoder[Move] = new TextDecoder[Move] {
    override def decode(txt: String): Either[TextDecodeError, Move] = {
      txt.split(" ") match {
        case Array(p, x1, y1, x2, y2) => {
          val player = p match {
            case "X" => Player.X
            case "O" => Player.O
          }
          Right(Move(player, Coords(x1.toInt, y1.toInt), Coords(x2.toInt, y2.toInt)))
        }
        case other =>
          Left(TextDecodeError)
      }
    }
  }

  implicit val outputMessageEncoder: TextEncoder[OutputMessage] = new TextEncoder[OutputMessage] {
    override def encode(m: OutputMessage): String = m match {
      case KeepAlive => "K"
      case PlayerJoined(p) => s"J ${Text.encode(p)}"
      case PlayerLeft(p) => s"L ${Text.encode(p)}"
      case GameStarted => "S"
      case MovePlayed(move, _) => "M " + moveEncoder.encode(move)
    }
  }
}
