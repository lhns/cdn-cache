package de.lhns

import _root_.cats.effect.IO
import de.lolhens.remoteio.Rest.RestClientImpl
import io.circe.Json
import org.http4s.dom.FetchClientBuilder
import org.http4s.implicits.http4sLiteralsSyntax
import org.scalajs.dom.document

import scala.scalajs.js

package object cdncache {
  implicit val restClient: RestClientImpl[IO] = RestClientImpl[IO](
    FetchClientBuilder[IO].create,
    uri"/api"
  )

  private def getMetaString(name: String): String =
    document.querySelector(s"""meta[name="$name"]""").asInstanceOf[js.Dynamic].content.toString

  private def getMetaJson(name: String): Json =
    io.circe.parser.parse(getMetaString(name)).toTry.get

  lazy val appConfig: AppConfig =
    getMetaJson("appconfig").as[AppConfig].toTry.get
}
