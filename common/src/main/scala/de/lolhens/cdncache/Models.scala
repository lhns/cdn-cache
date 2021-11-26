package de.lolhens.cdncache

import io.circe.Codec
import io.circe.generic.semiauto._

case class Mode(
                 passthrough: Boolean, // TODO: implement passthrough mode
                 record: Boolean
               )

object Mode {
  val default: Mode = Mode(
    passthrough = false,
    record = false,
  )

  implicit val codec: Codec[Mode] = deriveCodec
}

case class CacheEntry(
                       uri: String,
                       contentType: Option[String],
                       contentEncoding: Option[String],
                       contentLength: Option[Long],
                     )

object CacheEntry {
  implicit val codec: Codec[CacheEntry] = deriveCodec
}
