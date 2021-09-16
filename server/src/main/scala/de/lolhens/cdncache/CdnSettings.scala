package de.lolhens.cdncache

import de.lolhens.http4s.spa._
import io.circe.generic.semiauto._
import io.circe.{Codec, Decoder, Encoder}
import org.http4s.Uri

case class CdnSettings(
                        uri: Uri,
                        memCache: Option[Boolean],
                      ) {
  def memCacheOrDefault: Boolean = memCache.getOrElse(false)
}

object CdnSettings {
  implicit val codec: Codec[CdnSettings] = {
    implicit val uriCodec: Codec[Uri] = Codec.from(
      Decoder[String].emapTry(Uri.fromString(_).toTry),
      Encoder[String].contramap(_.renderString)
    )
    deriveCodec
  }

  def routeUri(name: String): Uri =
    if (name.isEmpty) Uri.Root
    else Uri.Root / name
}