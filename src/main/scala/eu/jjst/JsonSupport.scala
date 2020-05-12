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

  implicit val coordsEncoder: Encoder[Coords] = deriveEncoder[Coords]
  implicit val coordsDecoder: Decoder[Coords] = deriveDecoder[Coords]

  implicit val moveEncoder: Encoder[Move] = deriveEncoder[Move]
  implicit val moveDecoder: Decoder[Move] = deriveDecoder[Move]

  implicit val messageEncoder: Encoder[GameMessage] = deriveEncoder[GameMessage]
  implicit val messageDecoder: Decoder[GameMessage] = deriveDecoder[GameMessage]
}

object JsonSupport extends JsonSupport
