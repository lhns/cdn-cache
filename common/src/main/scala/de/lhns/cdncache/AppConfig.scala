package de.lhns.cdncache

import de.lhns.cdncache.AppConfig.CdnConfig
import io.circe.Codec
import io.circe.generic.semiauto._

case class AppConfig(
                      cdns: Seq[CdnConfig]
                    )

object AppConfig {
  implicit val codec: Codec[AppConfig] = deriveCodec

  case class CdnConfig(
                        routeUri: String,
                        uri: String,
                        enableMemCache: Boolean,
                      )

  object CdnConfig {
    implicit val codec: Codec[CdnConfig] = deriveCodec
  }
}
