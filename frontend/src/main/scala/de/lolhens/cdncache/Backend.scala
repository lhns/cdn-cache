package de.lolhens.cdncache

import cats.effect.IO
import io.circe.syntax._
import io.circe.{Json, parser}
import japgolly.scalajs.react.extra.Ajax
import scodec.bits.ByteVector

import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import scala.util.chaining.scalaUtilChainingOps

object Backend {
  private def request(
                       method: String,
                       url: String,
                       json: Option[Json]
                     ): IO[ByteVector] =
    Ajax(method, url)
      .setRequestContentTypeJsonUtf8
      .and(_.responseType = "arraybuffer")
      .pipe(e => json match {
        case Some(json) => e.send(json.noSpaces)
        case None => e.send
      })
      .asAsyncCallback
      .map { xhr =>
        xhr.status match {
          case 200 => ByteVector.view(TypedArrayBuffer.wrap(xhr.response.asInstanceOf[ArrayBuffer]))
          case status => throw new RuntimeException(s"Http Request returned status code $status!")
        }
      }


  private def jsonRequest(
                           method: String,
                           url: String,
                           json: Option[Json]
                         ): IO[Json] =
    request(method, url, json).map(bytes =>
      bytes.decodeUtf8.toTry.flatMap(parser.parse(_).toTry).get
    )

  def mode: IO[Mode] =
    jsonRequest("GET", "/api/mode", None).map(_.as[Mode].toTry.get)

  def setMode(mode: Mode): IO[Unit] =
    jsonRequest("POST", "/api/mode", Some(mode.asJson)).void

  def cacheEntries: IO[Seq[CacheEntry]] =
    jsonRequest("GET", "/api/cache/entries", None).map(_.as[Seq[CacheEntry]].toTry.get)
}
