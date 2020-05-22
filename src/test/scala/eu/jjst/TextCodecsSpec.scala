package eu.jjst

import eu.jjst.Models.{ Coords, Move, Player }
import org.scalatest.{ Matchers, WordSpec }

class TextCodecsSpec extends WordSpec with Matchers {

  import TextCodecs._
  "text codec" should {
    "encode and decode to same thing" in {
      val move = Move(Player.X, Coords(2, 1), Coords(0, 2))
      val encoded = Text.encode(move)
      val decoded = Text.decode(encoded)
      decoded shouldBe Right(move)
    }
  }
}
