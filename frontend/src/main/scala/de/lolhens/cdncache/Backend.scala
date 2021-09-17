package de.lolhens.cdncache

import cats.effect.IO
import io.circe.syntax._
import io.circe.{Json, parser}
import japgolly.scalajs.react.extra.Ajax
import org.scalajs.dom.document
import scodec.bits.ByteVector

import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, TypedArrayBuffer}
import scala.util.Success
import scala.util.chaining.scalaUtilChainingOps

object Backend {
  private def getMetaString(name: String): String =
    document.querySelector(s"""meta[name="$name"]""").asInstanceOf[js.Dynamic].content.toString

  private def getMetaJson(name: String): Json =
    io.circe.parser.parse(getMetaString(name)).toTry.get

  lazy val appConfig: AppConfig =
    getMetaJson("appconfig").as[AppConfig].toTry.get

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
      bytes.decodeUtf8.toTry.flatMap { string =>
        if (string.isEmpty) Success(Json.obj())
        else parser.parse(string).toTry
      }.get
    )

  def mode: IO[Mode] =
    jsonRequest("GET", "/api/mode", None).map(_.as[Mode].toTry.get)

  def setMode(mode: Mode): IO[Unit] =
    jsonRequest("POST", "/api/mode", Some(mode.asJson)).void

  def deleteEntry(uriPath: String): IO[Unit] =
    jsonRequest("POST", "/api/cache/entries/delete", Some(uriPath.asJson)).void

  def cacheEntries: IO[Seq[CacheEntry]] =
    jsonRequest("GET", "/api/cache/entries", None).map(_.as[Seq[CacheEntry]].toTry.get)
}
