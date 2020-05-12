package eu.jjst

import eu.jjst.Models.{ Coords, Move, Player }

trait TextEncoder[T] {
  def encode(t: T): String
}

trait TextDecoder[T] {
  def decode(txt: String): T
}

object Text {
  def decode[T](txt: String)(implicit textDecoder: TextDecoder[T]): T = textDecoder.decode(txt)
  def encode[T](t: T)(implicit textEncoder: TextEncoder[T]): String = textEncoder.encode(t)
}

object TextCodecs {
  implicit val moveEncoder: TextEncoder[Move] = new TextEncoder[Move] {
    override def encode(move: Move): String = {
      val player = move.player match {
        case Player.X => "X"
        case Player.O => "O"
      }
      s"$player ${move.outerBoardCoords.x} ${move.outerBoardCoords.y} ${move.innerBoardCoords.x} ${move.innerBoardCoords.y}"
    }
  }

  implicit val moveDecoder: TextDecoder[Move] = new TextDecoder[Move] {
    override def decode(txt: String): Move = {
      txt.split(" ") match {
        case Array(p, x1, y1, x2, y2) => {
          val player = p match {
            case "X" => Player.X
            case "O" => Player.O
          }
          Move(player, Coords(x1.toInt, y1.toInt), Coords(x2.toInt, y2.toInt))
        }
      }
    }
  }
}
