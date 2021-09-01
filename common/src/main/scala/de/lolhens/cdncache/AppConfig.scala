package de.lolhens.cdncache

import io.circe.Codec
import io.circe.generic.semiauto._

case class AppConfig(
                      cdnUri: String,
                      enableMemCache: Boolean,
                    )

object AppConfig {
  implicit val codec: Codec[AppConfig] = deriveCodec
}
