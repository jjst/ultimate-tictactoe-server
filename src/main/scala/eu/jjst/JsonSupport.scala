package eu.jjst

import eu.jjst.models.{ Coords, Move }
import io.circe.generic.extras.Configuration
import io.circe.{ Decoder, Encoder }
import io.circe.generic.extras.semiauto._

trait JsonSupport {
  implicit val conf: Configuration =
    Configuration.default
      .withDefaults
      .withDiscriminator("message_type")
      .withSnakeCaseMemberNames

  implicit val coordsEncoder: Encoder[Coords] = deriveEncoder
  implicit val coordsDecoder: Decoder[Coords] = deriveDecoder

  implicit val moveEncoder: Encoder[Move] = deriveEncoder
  implicit val moveDecoder: Decoder[Move] = deriveDecoder

  implicit val messageEncoder: Encoder[GameMessage] = deriveEncoder
  implicit val messageDecoder: Decoder[GameMessage] = deriveDecoder
}

object JsonSupport extends JsonSupport
