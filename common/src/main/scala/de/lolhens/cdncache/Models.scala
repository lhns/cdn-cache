package de.lolhens.cdncache

import io.circe.Codec
import io.circe.generic.semiauto._

case class Mode(record: Boolean)

object Mode {
  implicit val codec: Codec[Mode] = deriveCodec
}

case class CacheEntry(uri: String,
                      contentType: Option[String],
                      contentLength: Option[Long])

object CacheEntry {
  implicit val codec: Codec[CacheEntry] = deriveCodec
}
